(ns tailrecursion.pixel
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

(def conn
  (delay
   (d/connect (-> @config :datomic :uri))))

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

(defn strip
  "Removes everything except scalars and maps from expr."
  [expr]
  (let [pred #(or (not (coll? %)) (map? %))]
    (if (map? expr)
      (into {} (for [[k v] expr :when (pred v)]
                 [k (strip v)]))
      (if (pred expr) expr))))

(defn format-event [event]
  (let [stripped (strip event)]
    (if (contains? stripped "time")
      (update-in stripped ["time"] read-time)
      stripped)))

(defn generate-response
  [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/write-str data)})

(defroutes apiv1-routes
  (OPTIONS "/" [req] (generate-response {"status" "ok"}))
  (POST "/event"
        [entity-name :as req]
        (try
          (let [event (-> req :body slurp json/read-str format-event)]
            (d/transact-async
             @conn
             (datoms event (d/tempid :db.part/user) :pixel.event/ref))
            (generate-response {"status" "stored"}))
          (catch Throwable t
            (log/error "Error serving /apiv1/event" t)
            (generate-response {"status" "error"} 200)))))

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
  (run-jetty #'app (:jetty-options @config)))
