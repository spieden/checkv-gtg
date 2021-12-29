(ns checkv.client
  (:require
    [martian.hato :as hato]
    [martian.core :as martian]
    [clojure.core.memoize :as memo]
    [schema.core :as sch]))

(def username (System/getenv "CHECKVIST_USER"))
(def remote-key (System/getenv "CHECKVIST_KEY"))

(defmacro spy [id exp]
  `(let [v# ~exp]
    (tap> {~id v#})
    ~exp))

(def routes
  [{:route-name :login
    :path-parts ["/auth/login.json"]
    :method :get
    :query-schema {:username sch/Str
                   :remote_key sch/Str
                   :version sch/Str}
    :produces ["application/json"]}
   {:route-name :refresh
    :path-parts ["/auth/refresh_token.json"]
    :method :get
    :query-schema {:old_token sch/Str}
    :produces ["application/json"]}
   {:route-name :lists
    :path-parts ["/checklists.json"]
    :method :get
    :query-schema {:token sch/Str
                   :order sch/Str
                   :skip_stats sch/Str}
    :produces ["application/json"]}
   {:route-name :list
    :path-parts ["/checklists/" :id "/tasks.json"]
    :method :get
    :query-schema {:token sch/Str}
    :path-schema {:id sch/Str}
    :produces ["application/json"]}
   {:route-name :post-item
    :path-parts ["/checklists/" :list-id "/tasks.json"]
    :method :post
    :query-schema {:token sch/Str}
    :body-schema {:item sch/Any}
    :path-schema {:list-id sch/Str}
    :consumes ["application/json"]
    :produces ["application/json"]}])

(defn bootstrap
  ([] (bootstrap hato/default-opts))
  ([opts] (hato/bootstrap "https://checkvist.com"
                          routes
                          opts)))

(def client
  (atom (bootstrap)))

(defn response-for [route-name & [params]]
  (martian/response-for @client
                        route-name
                        params))

(defn request-for [route-name & [params]]
  (martian/request-for @client
                        route-name
                        params))

(defn get-token []
  (-> (response-for :login
                    {:username username
                     :remote_key remote-key
                     :version "2"})
      :body
      :token))

(defn refresh [old-token]
  (-> (response-for :refresh
                    {:old-token old-token})
      :body))

(def token
  (memo/ttl get-token
            :ttl/threshold (* 12 60 60 1000)))

(defn lists []
  (-> (response-for :lists
                    {:token (token)
                     :skip-stats "true"
                     :order "updated_at:desc"})
      :body
      vec))

(defn get-list [id]
  (-> (response-for :list
                    {:token (token)
                     :id (str id)})
      :body))

(defn push-ref-item
  [list-id item-doc]
  (let [item-doc {:content (str (:item/content item-doc)
                                " [origin](/cvt/" 
                                (:item/id item-doc)
                                ")")
                  :position 1
                  :tags (:item/tags-as-text item-doc)}]
    (-> (response-for :post-item
                      {:list-id (str list-id)
                       :token (token)
                       :item {:task item-doc}})
        :body)))

