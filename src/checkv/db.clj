(ns checkv.db
  (:require
    [clojure.java.io :as io]
    [com.rpl.specter :as spr]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [xtdb.api :as xt]
    [datomic.client.api :as d]))

(defn start-xtdb! []
  (letfn [(kv-store [dir]
            {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                        :db-dir (io/file dir)
                        :sync? true}})]
    (xt/start-node
     {:xtdb/tx-log (kv-store "data/dev/tx-log")
      :xtdb/document-store (kv-store "data/dev/doc-store")
      :xtdb/index-store (kv-store "data/dev/index-store")})))

(def xtdb-node (delay (start-xtdb!)))

(defn stop-xtdb! []
  (.close @xtdb-node))

(def schema
  [{:db/ident :list/user-updated-at
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :item/due-user-ids
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/many}
   {:db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/ident :list/items}
   {:db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/ident :item/id
    :db/valueType :db.type/long}
   {:db/ident :item/position
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident :list/task-count
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/ident :list/tags}
   {:db/ident :item/tags-as-text
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :item/status
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident :list/public
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one}
   {:db/ident :item/collapsed
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one}
   {:db/ident :list/percent-completed
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident :list/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :item/content
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/ident :item/list}
   {:db/ident :list/item-count
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident :list/user-count
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident :item/comments-count
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/ident :item/linked-items}
   {:db/ident :list/task-completed
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident :item/updated-at
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :item/update-line
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/ident :item/parent-item}
   {:db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/ident :item/tags}
   {:db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/ident :list/id
    :db/valueType :db.type/long}
   {:db/ident :list/read-only
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one}
   {:db/ident :list/markdown?
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one}
   {:db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/ident :tag
    :db/valueType :db.type/keyword}
   {:db/ident :item/tasks
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/many}
   {:db/ident :list/updated-at
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/ident :item/backlinks}
   {:db/ident :list/tags-as-text
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :list/options
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/many}
   {:db/ident :item/assignee-ids
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/many}
   {:db/ident :list/created-at
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :created_at
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :item/due
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :list/archived
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one}])


(def ref-attrs
  (into #{}
        (comp (filter #(= :db.type/ref
                          (:db/valueType %)))
              (map :db/ident))
        schema))

(defn add-tag-ents
  [tag-attr txn]
  (into txn
        (comp (mapcat (fn [doc]
                        (mapv #(hash-map :db/id %
                                         :tag (-> (edn/read-string %)
                                                  (second)))
                              (tag-attr doc))))
              (distinct))
        txn))

(defn forward-ref-decls
  [txn]
  (concat (into []
                (comp (distinct)
                      (map #(apply hash-map %)))
                (spr/select [spr/ALL ; all entites
                             spr/ALL ; all attr-val pairs
                             #(ref-attrs (first %)) ; only ref attrs
                             spr/LAST ; their vals
                             #(not= % []) ; not empty db.cardinality/many vals
                             (spr/if-path
                               #(vector? (first %)) spr/ALL ; smell test db.cardinality/many
                               spr/STAY)] ; otherwise assume db.cardinality/one
                            txn))
          txn))


(def db-name "checkv")


(def conn'
  (delay (let [client (d/client {:server-type :dev-local
                                 :system "dev"})
               _ (when-not ((set (d/list-databases client {}))
                            db-name)
                   (d/create-database client {:db-name db-name}))
               conn (d/connect client
                               {:db-name db-name})]
           (d/transact conn
                       {:tx-data schema})
           conn)))


(defn conn
  []
  @conn')


(defn q
  [query & args]
  (apply d/q
         query
         (d/db (conn))
         args))


(defn transact
  [txn]
  (prn :transact!)
  (-> (d/transact (conn)
                  {:tx-data txn})
      (:tx-data)
      (not-empty)))


(defn pull
  [eid]
  (d/pull (d/db (conn))
          [:*]
          eid))
