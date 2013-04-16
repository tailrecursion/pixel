;; This buffer is for notes you don't want to save, and for Lisp evaluation.
;; If you want to create a file, visit that file with C-x C-f,
;; then enter the text in that file's own buffer.

(require 'thefreshdiet.pixel)
(in-ns 'thefreshdiet.pixel)

(def conn (d/connect "datomic:sql://pixel_test?jdbc:postgresql://jenkins:5432/datomic?user=datomic&password=datomic"))

(q '[:find 
     :where
     [?e :pixel.event/server ?server]
     [?server :pixel.pair/key ?k1]
     [?server :pixel.pair/val ?v1]
     [(= ?k1 "REQUEST_METHOD")]
     [(= ?v1 "POST")]
     [?e :pixel.event/get ?get]
     [?get :pixel.pair/key ?k2]
     [?get :pixel.pair/val ?v2]
     [(= ?k2 "fn")]
     [(= ?v2 "4")]
     [?e :pixel.event/post ?post]
     [?post :pixel.pair/key ?k3]
     [?post :pixel.pair/val ?v3]
     [(= ?k3 "theDate")]
     [()]
     ]
     (db conn))

(q '[:find (count ?e)
     :where
     [?e :pixel.event/env ?env]
     [?env :pixel.pair/key ?k]
     [?env :pixel.pair/val ?v]
     [(= ?k "APPLICATION_ENV")]
     [(= ?v "production")]]
     (db conn))


