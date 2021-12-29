(ns checkv.db
  (:require
[clojure.java.io :as io]
[datahike.api :as d]
[com.rpl.specter :as spr]))

(def file 
  (io/file "db.datahike"))

(def config
  {:schema-flexibility :read
   :store {:backend :file 
           :path (.getAbsolutePath file)}})

(defn id-attr
  [attr]
  {:db/ident attr
   :db/valueType :db.type/long
   :db/cardinality :db.cardinality/one
   :db/unique :db.unique/identity})

(defn ref-attr
  [attr cardinality]
  {:db/ident attr
   :db/valueType :db.type/ref
   :db/cardinality cardinality})

(def schema
  [(id-attr :list/id)
   (id-attr :item/id)
   (id-attr :tag)
   (ref-attr :item/backlinks
             :db.cardinality/many)
   (ref-attr :item/list
             :db.cardinality/one)
   (ref-attr :item/linked-items
             :db.cardinality/many)
   (ref-attr :item/parent-item
             :db.cardinality/one)
   (ref-attr :item/tags
             :db.cardinality/many)
   (ref-attr :list/items
             :db.cardinality/one)
   (ref-attr :list/tags
             :db.cardinality/many) ])

(def ref-attrs
  (into #{}
        (comp (filter #(= :db.type/ref
                          (:db/valueType %)))
              (map :db/ident))
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
  (atom nil))

(defn conn
  []
  (if-not (.exists file)
    (do (d/create-database config)
        (d/transact (d/connect config)
                    schema)
        (reset! conn'
                (d/connect config)))
    (swap! conn'
           (fn [c]
             (if c
               c
               (d/connect config))))))

(defn q
  [query & args]
  (apply d/q
         query
         @(conn)
         args))

(defn transact
  [txn]
  (d/transact (conn)
              txn))

(defn pull
  [eid]
  (d/pull @(conn)
          [:*]
          eid))

