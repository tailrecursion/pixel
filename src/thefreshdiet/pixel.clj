(ns thefreshdiet.pixel
  (:require [datomic.api :refer [q db] :as d])
  (:import java.net.URI
           java.util.Date))

(def db-uri "datomic:mem://foo")

(defn setup! []
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]
    (d/transact
     conn
     [
      ;; environment key-value pairs
      {:db/id #db/id [:db.part/db]
       :db/ident :pair/key
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one
       :db.install/_attribute :db.part/db}
      {:db/id #db/id [:db.part/db]
       :db/ident :pair/val
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one
       :db.install/_attribute :db.part/db}

      ;; logged HTTP request event
      {:db/id #db/id [:db.part/db]
       :db/ident :event/request-uri
       :db/valueType :db.type/uri
       :db/cardinality :db.cardinality/one
       :db.install/_attribute :db.part/db}
      {:db/id #db/id [:db.part/db]
       :db/ident :event/referer-uri
       :db/valueType :db.type/uri
       :db/cardinality :db.cardinality/one
       :db.install/_attribute :db.part/db}
      {:db/id #db/id [:db.part/db]
       :db/ident :event/env
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/many
       :db.install/_attribute :db.part/db}
      ])))


(defn attach-pair [event-id [k v]]
  (let [pair-id (d/tempid :db.part/user)]
    [[:db/add pair-id :pair/key k]
     [:db/add pair-id :pair/val v]
     [:db/add event-id :event/env pair-id]]))

(defn attach-env [event-id env]
  (vec (mapcat (partial attach-pair event-id) (seq env))))

(defn append-event
  [request-uri referer-uri env]
  (let [conn (d/connect db-uri)
        event-id (d/tempid :db.part/user)]
    (d/transact
     conn
     (into [{:db/id event-id
             :event/request-uri request-uri
             :event/referer-uri referer-uri}]
           (attach-env event-id env)))))

(comment

  (let [conn (d/connect db-uri)]
    (d/transact
     conn
     [{:db/id #db/id [:db.part/user]
       :event/request-uri (URI. "http://abc.com")
       :event/referer-uri (URI. "http://def.com")}]))

  (d/entity (db (d/connect db-uri))
            (ffirst (q '[:find ?e :where [?e :event/request-uri]]
                       (db (d/connect db-uri)))))

  (let [conn (d/connect db-uri)]
    (d/transact
     conn
     [{:db/id #db/id [:db.part/user]
       :pair/key "foo"
       :pair/val "bar"}]))

  (let [conn (d/connect db-uri)]
    (d/transact
     conn
     [{:db/id #db/id [:db.part/user]
       :pair/key "name"
       :pair/val "Bob"}
      {:db/id #db/id [:db.part/user]
       :pair/key "age"
       :pair/val "38"}]))

  (let [conn (d/connect db-uri)
        pair-id (d/tempid :db.part/user)]
    (d/transact
     conn
     [[:db/add
       ;; id of the parent event
       #db/id[:db.part/user 17592186045420]
       ;; id of the pair we're adding
       :event/env #db/id[:db.part/user 17592186045422]]]))

  

  )