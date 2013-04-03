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
           :db-uri "datomic:sql://pixel_test?jdbc:postgresql://jenkins.thefreshdiet.trmk:5432/datomic?user=datomic&password=datomic"}
   "prod" {:env :prod
           :db-uri "datomic:sql://pixel_prod?jdbc:postgresql://jenkins.thefreshdiet.trmk:5432/datomic?user=datomic&password=datomic"}})

(def cfg
  "Looks at the ENV OS environment variable and derefs to the
corresponding config map value.  Defaults to prod."
  (delay
   (let [env (or (System/getenv "ENV") "prod")]
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
   {:db/ident :pixel.event/env
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
  "Provided an event id and a [k v] pair, attaches the pair to the
  event's environment map.  Returns a tx."
  [event-id [k v]]
  (let [pair-id (d/tempid :db.part/user)]
    [[:db/add pair-id :pixel.pair/key k]
     [:db/add pair-id :pixel.pair/val v]
     [:db/add event-id :pixel.event/env pair-id]]))

(defn attach-env
  "Provided an event id and a map of strings to strings, returns a tx
  with the env attached."
  [event-id env]
  (vec (mapcat (partial attach-pair event-id) (seq env))))

(defn store-event!
  "Transacts information about an event."
  [request-uri referer-uri time env]
  (let [conn (d/connect (:db-uri @cfg))
        event-id (d/tempid :db.part/user)]
    (d/transact-async
     conn
     (into [{:db/id event-id
             :pixel.event/request-uri request-uri
             :pixel.event/referer-uri referer-uri
             :pixel.event/time time}]
           (attach-env event-id env)))))

;;; HTTP routing

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defroutes apiv1-routes
  (POST "/event" [request-uri referer-uri time env]
        (store-event! request-uri referer-uri time env)
        (generate-response {:status :stored})))

(defroutes app-routes
  (context "/apiv1" [] (-> apiv1-routes wrap-edn-params))
  (not-found "Not found."))

(def app app-routes)

(defn -main [^String port]
  (run-jetty app-routes {:port (Integer. port)}))
