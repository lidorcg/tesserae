(ns tesserae.push-notif
  (:require [stuffs.util :as su]
            [webpush.core :as wp]
            [webpush.utils :as wu]
            [mount.core :as mount :refer [defstate]]
            [tesserae.db :as db]
            [missionary.core :as m]
            [stuffs.env :as env]
            ))

(comment
  (wp/add-security-provider!)
  (wu/generate-keys)
  )

(defstate ^{:on-reload :noop} service
  :start (do
           (wp/add-security-provider!)
           (wp/service
            (env/get :web-push-public-key)
            (env/get :web-push-private-key)
            "mailto:dennis@lumber.dev")))

;(map :v (db/datoms :ave :user/web-push-subs))

(defn send! [{:web-push-sub/keys [endpoint p256dh auth] :as sub-ent} notif-m]
  (let [;; Create a subscription
        sub   (wp/subscription endpoint p256dh auth)
        ;; Create a push-service instance by passing your `public-key`,
        ;; `private-key` (as strings), and the webpush `subject` (most of the time
        ;; your email)
        notif (wp/notification sub (su/write-json-string notif-m))]
    (wp/send! service notif))
  )

(defn sub-gone? [resp]
  (-> resp .getStatusLine .getStatusCode (= 410)))

(defn send-to-many! [notif-m web-push-ents]
  (when-let [gone-subs (not-empty
                         (sequence
                           (comp (map (juxt :db/id #(send! % notif-m)))
                                 (keep (fn [[eid resp]]
                                         (when (-> resp .getStatusLine .getStatusCode (= 410))
                                           [:db/retractEntity eid]))))
                           web-push-ents))]
    (db/transact! gone-subs)))

(defn send-to-all! [notif-m]
  (send-to-many! notif-m (db/datoms->entities :ave :web-push-sub/auth)))
#_#_(sort-by identity > [1 3 4])
        (map tap> (sort-by :db/created-at > (db/datoms->entities :ave :web-push-sub/auth)))

;(map datalevin.core/touch(db/datoms->entities :ave :web-push-sub/auth))
;(map :web-push-sub/endpoint  (db/datoms->entities :ave :web-push-sub/auth))


(comment
  (def reqs (send-to-all! {:title      "yssaa me"
                                 :body "hey"})))
