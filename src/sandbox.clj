(ns sandbox 
  (:require [checkv.core :as core]
            [hashp.core]
            [xtdb.api :as xt]
            [checkv.db :as db]))

(defn sandbox
  []
  (core/changed-txn)
  #_(db/q '{:find [?i ?l]
          :in [?list-id]
          :where [[?l :list/id ?list-id]
                  [?i :item/list ?l]
                  [?i :item/updated-at ?updated-at]]}
        830192)
  #_(core/last-item-updated-at 830192)
  #_[(core/pending-ref-items)
     (core/sync-ref-items)]
  #_(core/tick)
  #_(db/transact (core/changed-txn nil))
  #_(db/q '{:find [(pull ?l [*])
                   (pull ?i [*])]
            :in [?li ?ii]
            :where [[?l :list/id ?li]
                    [?i :item/id ?ii]]}
          824465
          53730090)
  #_(core/pending-ref-items)
  #_(db/q '{:find [?l ?n ?t]
            :where [[?l :list/tags ?t]
                    [?l :list/name ?n]]})

  #_(core/sync-lists)
  #_(do
      (core/sync-changed-items)
      (->> (core/pending-ref-items)
           (sort-by #(-> % second :item/updated-at)))l))

