(ns checkv.core
  (:require
    [checkv.client :as client]
    [hashp.core]
    [checkv.db :as db]
    [checkv.marshal :as marshal]
    [xtdb.api :as xt]))

(defn sync-lists
  []
  (->> (client/lists)
       (map marshal/list-doc->txn-ent) 
       (db/transact)))

(defn sync-items
  [list-id]
  (->> (client/get-list list-id)
       (map marshal/item-doc->txn-ent)
       (db/transact)))

(defn last-list-update
  []
  (ffirst (db/q '{:find [(max ?updated-at)]
                  :where [[_ :list/updated-at ?updated-at]]})))

(defn updated-since?
  [last-seen-update {updated :updated_at}]
  (< (compare last-seen-update
              updated)
     0))

(defn changed-lists
  [last-seen-update]
  (into []
        (filter (partial updated-since?
                         last-seen-update))
        (client/lists)))

(defn changed-items
  [list-id]
  (let [last-seen-update (ffirst (db/q '{:find [(max ?updated-at)]
                                         :in [?list-id]
                                         :where [[?i :item/list-id ?list-id]
                                                 [?i :item/updated-at ?updated-at]]}
                                       list-id))]
    (into []
          (filter (partial updated-since?
                           last-seen-update))
          (client/get-list list-id))))

(defn changed-txn
  ([]
   (changed-txn (last-list-update)))
  ([last-list-update]
   (when-let [lists (not-empty (changed-lists last-list-update))]
     (into (mapv marshal/list-doc->txn-ent
                 lists)
           (comp (mapcat (fn [{list-id :id}]
                           (if last-list-update
                             (changed-items list-id)
                             (client/get-list list-id))))
                 (map marshal/item-doc->txn-ent))
           lists))))

(defn sync-changed
  []
  (when-let [txn (changed-txn)]
    (db/transact txn)
    txn))

(defn pending-ref-items
  []
  (db/q '{:find [?list-id
                 (pull ?item [:item/content
                              :item/tags-as-text
                              :item/id
                              :item/status
                              :item/updated-at
                              :item/linked-items
                              {:item/list [:list/id
                                           :list/tags-as-text]}])]
          :where [[?item :item/status 0] ; item is "open"

                  ; item and target list share at least one tag
                  [?item :item/tags ?tag]
                  [?target-list :list/tags ?tag]

                  ; item not from the target list
                  (not [?item :item/list ?target-list])

                  ; item not already linked from target list
                  (not-join [?target-list
                             ?item]
                            [?existing-item :item/list ?target-list]
                            [?existing-item :item/linked-items ?item])

                  ; item's list doesn't have any tags (is a journal)
                  ; this prevents ping-pong between tag lists
                  [?item :item/list ?item-list]
                  [?item-list :list/tags-as-text ""]

                  [?target-list :list/id ?list-id]]}))

(defn sync-ref-items
  []
  (mapv #(let [[list-id item-ent] %]
           (client/push-ref-item list-id
                                 item-ent))
        (sort-by #(-> % second :item/updated-at)
                 (pending-ref-items))))

(defn tick
  []
  (when-let [changed (sync-changed)]
    (prn changed)
    (prn (sync-ref-items))))

(defn -main
  []
  (loop []
    (tick)
    (Thread/sleep 5000)
    (recur)))

