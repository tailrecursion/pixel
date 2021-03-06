(defproject tailrecursion/pixel "1.0.0-SNAPSHOT"
  :description "TFD usage tracking server."
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring/ring-core "1.1.6"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [compojure "1.1.3"]
                 [org.clojure/data.json "0.2.2"]
                 [org.clojure/tools.logging "0.2.6"]
                 [log4j "1.2.15"
                  :exclusions [javax.mail/mail
                               javax.jms/jms
                               com.sun.jdmk/jmxtools
                               com.sun.jmx/jmxri]]
                 [org.slf4j/slf4j-log4j12 "1.6.6"]
                 [org.clojure/tools.nrepl "0.2.2"]]
  :main tailrecursion.pixel
  :profiles {:dev
             {:resource-paths ["envs/dev"]
              :dependencies [[tailrecursion/monocopy "1.0.10-SNAPSHOT"
                              :exclusions [org.clojure/clojure
                                           org.slf4j/slf4j-nop
                                           org.slf4j/log4j-over-slf4j]]]
              :plugins      [[lein-ring "0.8.3"]]
              :ring {:handler tailrecursion.pixel/app}}})
