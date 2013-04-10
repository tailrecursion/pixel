(ns thefreshdiet.pixel
  (:require [datomic.api           :refer [q db] :as d]
            [compojure.core        :refer [defroutes context POST]]
            [compojure.route       :refer [not-found]]
            [ring.adapter.jetty    :refer [run-jetty]]
            [clojure.tools.logging :refer [info error] :as log]
            [compojure.handler     :as    handler]
            [clojure.edn           :as    edn])
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
  [{:db/ident :pixel.pair/key
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident :pixel.pair/val
    :db/id #db/id [:db.part/db]
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}])

(defn load!
  "Creates the database at db-uri and loads txs. Dev/testing only."
  []
  (d/delete-database (:db-uri @cfg))
  (d/create-database (:db-uri @cfg))
  (let [conn (d/connect (:db-uri @cfg))]
    (d/transact conn schema))
  (def conn (d/connect (:db-uri @cfg))))

;;; Transactions

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
  (vec (mapcat (partial attach-pair eid attr) (seq m))))

(defn concatv
  [& colls]
  (reduce into (map vec colls)))

(def base-name "pixel.")

(defn genschema1
  "Generates schema for the entity at ns using value type information
  in v."
  [ns [k v]]
  (merge {:db/ident (keyword ns (name k))
          :db/id (d/tempid :db.part/db)
          :db.install/_attribute :db.part/db}
         (cond
          (string? v)
          {:db/valueType :db.type/string
           :db/cardinality :db.cardinality/one}
          (= Long (class v))
          {:db/valueType :db.type/long
           :db/cardinality :db.cardinality/one}
          (= Double (class v))
          {:db/valueType :db.type/long
           :db/cardinality :db.cardinality/one}
          (map? v)
          {:db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many}
          :else (throw (IllegalArgumentException.
                        (format "can't make schema for value %s of type %s"
                                (pr-str v)
                                (type v)))))))

(defn genschema
  [ns attr-map]
  (mapv (partial genschema1 ns) attr-map))

(defn gendata
  [ns attr-map]
  (let [{scalars false maps true} (group-by (comp map? second) attr-map)
        eid (d/tempid :db.part/user)]
    (apply concatv
     [(apply merge
             {:db/id eid}
             (for [[k v] scalars]
               [(keyword ns (name k)) v]))]
     (for [[k v] maps]
       (attach-map eid (keyword ns (name k)) v)))))

(defn append!
  [entity-name attr-map]
  (let [ns (str base-name entity-name)
        conn (d/connect (:db-uri @cfg))]
    (d/transact conn (genschema ns attr-map))
    (d/transact conn (gendata ns attr-map))))

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defroutes apiv1-routes
  (POST "/entity/:entity-name"
        [entity-name :as req]
        (try
          (let [attr-map (-> req :body slurp edn/read-string)]
            (append! entity-name attr-map)
            (println (format "stored a %s%s with %s"
                             base-name entity-name (pr-str attr-map)))
            (generate-response {:status :stored}))
          (catch Throwable t
            (println (format "ERROR storing a %s%s" base-name entity-name))
            (generate-response {:status :error, :error t})))))

(defroutes app
  (context "/apiv1" [] (-> apiv1-routes handler/api))
  (not-found "Not found."))

(defn -main [^String port]
  (load!)
  (run-jetty #'app {:port (Integer. port)}))
