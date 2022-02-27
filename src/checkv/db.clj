(ns checkv.db
  (:require
    [clojure.java.io :as io]
    [xtdb.api :as xt]))

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

(defn q
  [query & args]
  (apply xt/q
         (xt/db @xtdb-node)
         query
         args))

(defn transact
  [txn]
  (let [result (xt/submit-tx @xtdb-node txn)]
    (xt/sync @xtdb-node)
    result))

