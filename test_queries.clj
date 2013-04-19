;;; setup
(do
  (System/setProperty "datomic.memcachedServers", "jenkins.thefreshdiet.trmk:11211")
  (require '[datomic.api :refer [q db] :as d])
  (import java.net.URI java.util.Date)
  (def conn (d/connect "datomic:sql://pixel_prod2?jdbc:postgresql://jenkins.thefreshdiet.trmk:5432/datomic?user=datomic&password=datomic")))

;;; slowwww
(dotimes [_ 100]
  (time
   (q '[:find (count ?e)
        :where
        [?e :pixel.event/env ?env]
        [?env :pixel.pair/key "APPLICATION_ENV"]
        [?env :pixel.pair/val "production"]]
      (db conn))))

(q '[:find (count ?e)
     :where
     [?e :pixel.event/status 200]
     [?e :pixel.event/env ?env]
     [?env :pixel.pair/key "APPLICATION_ENV"]
     [?env :pixel.pair/val "production"]]
   (db conn))