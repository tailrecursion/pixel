(defproject thefreshdiet/pixel "0.0.1"
  :description "TFD usage tracking server."
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.datomic/datomic-pro "0.8.3862"]
                 [ring/ring-core "1.1.6"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [compojure "1.1.3"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/data.json "0.2.2"]]
  :plugins      [[lein-ring "0.8.3"]]
  :ring {:handler thefreshdiet.pixel/app})
