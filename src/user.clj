(ns user
  (:require
   [checkv.client :as client]
   [clojure.tools.namespace.repl]
   [martian.hato :as hato]
   [martian.interceptors :refer [inject]]
   [martian.vcr :as vcr]
   [portal.api :as portal]
   [checkv.marshal :as marshal]
   [checkv.db :as db]
   [checkv.core :as core]))

(defn portal []
  (let [p (portal/open {:portal.launcher/port 5822
                        :portal.launcher/host "vault"})]
    (portal/tap)
    p))

(def vcr-opts {:store {:kind :file
                       :root-dir "/tmp/"
                       :pprint? true}})

(defn record!
  []
  (reset! client/client
          (client/bootstrap {:interceptors (inject hato/default-interceptors
                                                   (vcr/record vcr-opts)
                                                   :after hato/perform-request)})))

(defn playback!
  []
  (reset! client/client
          (client/bootstrap {:interceptors (inject hato/default-interceptors
                                                   (vcr/playback vcr-opts)
                                                   :after hato/perform-request)})))

(defn do-it-live!
  []
  (reset! client/client
          (client/bootstrap)))

(defn sandbox
  []
  (core/sync-changed-items)
  (core/sync-ref-items))

