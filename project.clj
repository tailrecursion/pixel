(defproject thefreshdiet/pixel "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [com.datomic/datomic-pro "0.8.3826" :exclusions [postgresql]]
                 [ring/ring-core "1.1.6"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [compojure "1.1.3"]
                 [ring-edn "0.1.0"]])
