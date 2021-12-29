(ns checkv.core
  (:require [checkv.marshal :as marshal]
            [checkv.client :as client]
            [checkv.db :as db]))

(defn sync-lists
  []
  (db/transact (->> (client/lists)
                    (map marshal/list-doc->txn-ent)
                    (db/forward-ref-decls))) )

(defn sync-items
  [list-id]
  (->> (client/get-list list-id)
       (into []
             (map (comp marshal/filter-nil-valued
                        marshal/item-doc->txn-ent)))
       (db/forward-ref-decls)
       #_(db/transact)))

(defn last-seen-update
  []
  (->> (db/q '{:find [?updated-at]
               :where [[_ :list/updated-at ?updated-at]]})
       (mapv first)
       (sort)
       (last)))

(defn sync-changed-items
  []
  (let [last-update (last-seen-update)
        lists-txn-frag (into []
                            (comp (filter #(< (compare last-update
                                                       (:updated_at %))
                                              0))
                                  (map marshal/list-doc->txn-ent))
                            (client/lists))
        items-txn-frag (into []
                             (comp (mapcat #(client/get-list (:list/id %)))
                                   (map (comp marshal/filter-nil-valued
                                              marshal/item-doc->txn-ent)))
                             lists-txn-frag)]
    (some->> (into lists-txn-frag
                   items-txn-frag)
             (not-empty)
             (db/forward-ref-decls)
             (db/transact))))

(defn pending-ref-items
  []
  (db/q '{:find [?list-id
                 (pull ?item [:item/content
                              :item/tags-as-text
                              :item/id
                              :item/updated-at
                              {:item/linked-items [:item/id]}
                              {:item/list [:list/id]}])]
          :where [[?item :item/tags ?tag]
                  [?list :list/tags ?tag]
                  (not [?item :item/list ?list])
                  (not [?existing :item/list ?list]
                       [?existing :item/linked-items ?item])
                  [?list :list/id ?list-id]]}))

(defn sync-ref-items
  []
  (mapv #(let [[list-id item-ent] %]
           (client/push-ref-item list-id
                                 item-ent))
        (sort-by #(-> % second :item/updated-at)
                 (pending-ref-items))))

(defn -main
  []
  (loop []
    (when-let [changed (sync-changed-items)]
      (prn changed)
      (prn (sync-ref-items)))
    (Thread/sleep 5000)
    (recur)))

