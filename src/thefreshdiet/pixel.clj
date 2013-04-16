(ns thefreshdiet.pixel
  (:require [datomic.api           :refer [q db] :as d]
            [compojure.core        :refer [defroutes context POST]]
            [compojure.route       :refer [not-found]]
            [ring.adapter.jetty    :refer [run-jetty]]
            [clojure.tools.logging :refer [info error] :as log]
            [compojure.handler     :as    handler]
            [clojure.data.json     :as    json]
            [clojure.edn           :as    edn])
  (:import java.net.URI
           java.util.Date))

(def envs
  "Map of environment name strings to settings maps.  Settings maps
  should contain, at a minimum, a :db-uri key.  The \"prod\"
  environment key is required."
  {"dev"  {:env :dev
           :db-uri "datomic:mem://pixel_dev"}
   "prod" {:env :test
           :db-uri "datomic:sql://pixel_test?jdbc:postgresql://jenkins:5432/datomic?user=datomic&password=datomic"}})

(def cfg
  "Looks at the ENV OS environment variable and derefs to the
corresponding config map value.  Defaults to dev (in-memory Datomic)."
  (delay
   (let [env (or (System/getenv "ENV") "dev")]
     (assert (contains? envs env))
     (info "pixel environment:" env)
     (get envs env))))

;;; Schema

(def schema
  [;; string => string map support
   {:db/ident :pixel.pair/key
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.pair/val
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   ;; maps
   {:db/ident :pixel.event/cookie
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/env
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/files
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/get
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/post
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/ref
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
   {:db/ident :pixel.event/server
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/session
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   ;; scalars
   {:db/ident :pixel.event/status
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.event/time
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   ;; collections
   {:db/ident :pixel.event/tags
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}])

(defn load!
  "Creates the database at db-uri and loads txs. Dev/testing only."
  []
  (d/delete-database (:db-uri @cfg))
  (d/create-database (:db-uri @cfg))
  (let [conn (d/connect (:db-uri @cfg))]
    (d/transact conn schema))
  (when (= :dev (:env @cfg))
    (def conn (d/connect (:db-uri @cfg)))))

(defn attach-pair
  "Provided an entity id, an attribute name, and a [k v] pair, attaches
  the pair to the named attributed of the event.  Returns a tx."
  [eid attr [k v]]
  (let [pair-id (d/tempid :db.part/user)]
    [[:db/add pair-id :pixel.pair/key k]
     [:db/add pair-id :pixel.pair/val v]
     [:db/add eid attr pair-id]]))

(defn attach-map
  "Provided an entity id, an attribute name, and a map of strings to
  strings, returns a tx with the env attached."
  [eid attr m]
  {:pre [(map? m)]}
  (vec (mapcat (partial attach-pair eid attr) (seq m))))

(defn concatv
  "Converts colls to vectors and concatenates them into a vector"
  [& colls]
  (reduce into (map vec colls)))

(defn attach-maps
  "Provided an eid and a map of ident keywords to string->string maps,
  emits txes for associating the maps with eid."
  [eid attrs->maps]
  (reduce concatv (map (fn [[a m]] (attach-map eid a m)) attrs->maps)))

(defn read-time [rfc3339]
  (edn/read-string (str "#inst" (pr-str rfc3339))))

(defn event-txs
  "Generates a vector of txes provided an event map."
  [event]
  (let [eid (d/tempid :db.part/user)]
    (concatv
     [{:db/id              eid
       :pixel.event/status (get event ":status")
       :pixel.event/time   (read-time (get event ":time"))
       }]
     (mapv #(vector :db/add eid :pixel.event/tags %) (get event ":tags"))
     (attach-maps eid
                  {:pixel.event/cookie           (get event ":cookie")
                   :pixel.event/env              (get event ":env")
                   :pixel.event/files            (get event ":files")
                   :pixel.event/get              (get event ":get")
                   :pixel.event/post             (get event ":post")
                   :pixel.event/request-headers  (get event ":request-headers")
                   :pixel.event/response-headers (get event ":response-headers")
                   :pixel.event/server           (get event ":server")
                   :pixel.event/session          (get event ":session")}))))

(defn append-event!
  "Appends the event map to Datomic."
  [event]
  (d/transact-async (d/connect (:db-uri @cfg)) (event-txs event)))

(defn generate-response
  [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/write-str data)})

(defroutes apiv1-routes
  (POST "/event"
        [entity-name :as req]
        (let [body (-> req :body slurp)]
          (try
            (let [event (json/read-str body)]
              (append-event! event)
              (println (java.util.Date.) " /apiv1/event")
              (generate-response {"status" "stored"}))
            (catch RuntimeException e
              (println "***** STARTERROR *****")
              (println body)
              (println "***** ENDERROR *****")
              (generate-response {"status" "error"} 500))))))

(defroutes app
  (context "/apiv1" [] (-> apiv1-routes handler/api))
  (not-found "Not found."))

(defn -main [^String port]
  (run-jetty #'app {:port (Integer. port)}))
