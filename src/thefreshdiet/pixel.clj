(ns thefreshdiet.pixel
  (:require [datomic.api                :refer [q db] :as d]
            [compojure.core             :refer [defroutes context POST OPTIONS]]
            [compojure.route            :refer [not-found]]
            [ring.adapter.jetty         :refer [run-jetty]]
            [tailrecursion.monocopy     :refer [datoms hydrate] :as mc]
            [compojure.handler          :as    handler]
            [clojure.data.json          :as    json]
            [clojure.edn                :as    edn]
            [clojure.java.io            :as    io]
            [clojure.tools.logging      :as    log]
            [clojure.tools.nrepl.server :as    nrepl]))

;;; configuration

(def config
  (delay
   (if-let [resource (io/resource "config.edn")]
     (-> resource slurp edn/read-string)
     (throw (RuntimeException. "No config.edn found for this environment.")))))

;;; application

(def schema
  [{:db/doc "Points to a monocopy map of event information."
    :db/ident :pixel.event/ref
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}])

(defn read-time [rfc3339]
  (edn/read-string (str "#inst" (pr-str rfc3339))))

(defn format-event [event]
  (update-in event ["time"] read-time))

(defn generate-response
  [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/write-str data)})

(def ^:dynamic *conn* nil)

(defroutes apiv1-routes
  (OPTIONS "/" [req] (generate-response {"status" "ok"}))
  (POST "/event"
        [entity-name :as req]
        (log/info "serving /apiv1/event")
        (let [event (-> req :body slurp json/read-str format-event)]
          (d/transact-async *conn* (datoms event (d/tempid :db.part/user) :pixel.event/ref))
          (generate-response {"status" "stored"}))))

(defroutes app
  (context "/apiv1" [] (-> apiv1-routes handler/api))
  (not-found "Not found."))

(defn -main [& args]
  ;; create in-memory datomic, transact schema when in :dev mode
  (when (= (:env @config) :dev)
    (d/create-database (-> @config :datomic :uri))
    (d/transact (d/connect (-> @config :datomic :uri))
                (concat mc/schema schema)))
  ;; start a repl server if configured
  (when-let [repl-options (:repl-options @config)]
    (apply nrepl/start-server (apply concat repl-options)))
  ;; connect to datomic and start jetty
  (binding [*conn* (d/connect (-> @config :datomic :uri))]
    (run-jetty #'app (:jetty-options @config))))
