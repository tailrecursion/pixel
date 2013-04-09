(ns thefreshdiet.pixel
  (:require [datomic.api           :refer [q db] :as d]
            [compojure.core        :refer [defroutes context POST]]
            [compojure.route       :refer [not-found]]
            [ring.adapter.jetty    :refer [run-jetty]]
            [ring.middleware.edn   :refer [wrap-edn-params]]
            [clojure.tools.logging :refer [info error] :as log])
  (:import java.net.URI
           java.util.Date))

;;; Configuration

(def envs
  "Map of environment name strings to settings maps.  Settings maps
  should contain, at a minimum, a :db-uri key.  The \"prod\"
  environment key is required."
  {"dev"  {:env :dev
           :db-uri "datomic:mem://pixel_dev"}
   "test" {:env :test
           :db-uri "datomic:sql://pixel_test?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"}
   "prod" {:env :prod
           :db-uri "datomic:sql://pixel_prod?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"}})

(def cfg
  "Looks at the ENV OS environment variable and derefs to the
corresponding config map value.  Defaults to prod."
  (delay
   (let [env (or (System/getenv "ENV") "dev")]
     (assert (contains? envs env))
     (info "pixel environment:" env)
     (get envs env))))

;;; Schema

(def schema
  "Pixel schema.  Event entities have request URI, referer URI,
  instant, and environment associations."
  [{:db/ident :pixel.pair/key
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.pair/val
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/ident :pixel.event/request-uri
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/uri
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/request-method
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/referer-uri
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/uri
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/time
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/status
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/tags
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/request-headers
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/response-headers
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/session
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}])

(defn load!
  "Creates the database at db-uri and loads txs. Dev/testing only."
  [db-uri txs]
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]
    (d/transact conn txs)))

;;; Transactions

(defn attach-pair
  "Provided an event id, an attribute name, and a [k v] pair, attaches
  the pair to the named attributed of the event.  Returns a tx."
  [event-id attr [k v]]
  (let [pair-id (d/tempid :db.part/user)]
    [[:db/add pair-id :pixel.pair/key k]
     [:db/add pair-id :pixel.pair/val v]
     [:db/add event-id attr pair-id]]))

(defn attach-map
  "Provided an event id, an attribute name, and a map of strings to
  strings, returns a tx with the env attached."
  [event-id attr m]
  (vec (mapcat (partial attach-pair event-id attr) (seq m))))

(defn concatv
  [& colls]
  (reduce into (map vec colls)))

(defn store-event!
  "Transacts information about an event."
  [[request-uri
    request-method
    referer-uri
    time
    status
    tags
    request-headers
    response-headers
    session
    :as event]]
  (println "storing " (pr-str event))
  (let [conn (d/connect (:db-uri @cfg))
        event-id (d/tempid :db.part/user)]
    (d/transact-async
     conn
     (concatv
      [{:db/id event-id
        :pixel.event/request-uri request-uri
        :pixel.event/referer-uri referer-uri
        :pixel.event/request-method request-method
        :pixel.event/time time
        :pixel.event/status status}]
      (mapv #(vector :db/add event-id :pixel.event/tags %) tags)
      (attach-map event-id :pixel.event/request-headers request-headers)
      (attach-map event-id :pixel.event/response-headers response-headers)
      (attach-map event-id :pixel.event/session session)))))

;;; HTTP routing

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defroutes apiv1-routes
  (POST "/event" [request-uri           ;string
                  request-method        ;string
                  referer-uri           ;string
                  time                  ;java.util.Date
                  status                ;long
                  tags                  ;vector of strings
                  request-headers       ;map of string => string
                  response-headers      ;map of string => string
                  session               ;map of string => string
                  ]
        (store-event! [request-uri
                       request-method
                       referer-uri
                       time
                       status
                       tags
                       request-headers
                       response-headers
                       session])
        (generate-response {:status :stored})))

(defroutes app-routes
  (context "/apiv1" [] (-> apiv1-routes wrap-edn-params))
  (not-found "Not found."))

(def app app-routes)

(defn -main [^String port]
  (run-jetty app-routes {:port (Integer. port)}))
