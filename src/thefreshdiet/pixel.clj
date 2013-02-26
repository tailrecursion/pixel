(ns thefreshdiet.pixel
  (:require [datomic.api :refer [q db] :as d]))

(def db-uri "datomic:mem://foo")

(defn append-event
  "takes fully cooked stuff"
  [request-uri referer-uri date env]
  (let [conn (d/connect db-uri)]
    (d/transact
     conn
     {:db/id #db/id [:db.part/user]
      :event/request-uri request-uri
      :event/referer-uri referer-uri
      :event/time date})))