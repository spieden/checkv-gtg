(ns checkv.db
  (:require
    [clojure.java.io :as io]
    [datalevin.core :as d]
    [com.rpl.specter :as spr]))

(def schema
  {:list/items {:db/valueType :db.type/ref
                :db/cardinality :db.cardinality/one}
   :item/id {:db/cardinality :db.cardinality/one
             :db/unique :db.unique/identity}
   :list/tags {:db/valueType :db.type/ref
               :db/cardinality :db.cardinality/many}
   :item/list {:db/valueType :db.type/ref
               :db/cardinality :db.cardinality/one}
   :item/linked-items {:db/valueType :db.type/ref
                       :db/cardinality :db.cardinality/many}
   :item/parent-item {:db/valueType :db.type/ref
                      :db/cardinality :db.cardinality/one}
   :item/tags {:db/valueType :db.type/ref
               :db/cardinality :db.cardinality/many}
   :list/id {:db/cardinality :db.cardinality/one
             :db/unique :db.unique/identity}
   :tag {:db/cardinality :db.cardinality/one
         :db/unique :db.unique/identity}
   :item/backlinks {:db/valueType :db.type/ref
                    :db/cardinality :db.cardinality/many}})

(def ref-attrs
  (into #{}
        (comp (filter #(= :db.type/ref
                          (:db/valueType (val %))))
              (map key))
        schema))

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

(def conn'
  (delay (d/get-conn "checkv.dtlv"
                     schema)))

(defn conn
  []
  @conn')

(defn q
  [query & args]
  (apply d/q
         query
         @(conn)
         args))

(defn transact
  [txn]
  (-> (d/transact! (conn)
                   txn)
      (:tx-data)
      (not-empty)))

(defn pull
  [eid]
  (d/pull @(conn)
          [:*]
          eid))

