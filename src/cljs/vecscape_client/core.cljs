(ns vecscape-client.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [cljs.core.async :refer [<! timeout]]
              [vecscape-client.data :refer [make-initial-db]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ASPECT [21 9])

;; -------------------------
;; Functions

(defn now []
  (.getTime (js/Date.)))

(defn rectangle-in-rectangle [r1 r2]
  (let [scale (js/Math.min (/ (first r2) (first r1)) (/ (last r2) (last r1)))]
    (map #(int (* % scale)) r1)))

(defonce loop-instance (atom 0))

(defn launch-game-loop! [db]
  (let [instance-id (swap! loop-instance inc)]
    (print "Game loop" instance-id)
    (go (loop []
          (when (= instance-id @loop-instance)
            (<! (timeout 100))
            (swap! db assoc :timestamp (now))
            (recur))
          (print "Exiting old game loop")))))

(defn add-entity! [db id e]
  (swap! db assoc-in [:entities id] e))

;; -------------------------
;; Events

(defn event-resize [c ev]
  (reset! c
          (vec (rectangle-in-rectangle [21 9] [(.-innerWidth js/window) (.-innerHeight js/window)]))))

;; -------------------------
;; Views

(def renderers
  {:player
   (fn [db id e]
     (let [y-offset (* 0.08 (js/Math.sin (* 0.004 (- (e :timestmap) (db :timestamp)))))]
     [:g {:key id}
      [:circle {:r 0.5 :cx 0 :cy (- -0.9 y-offset) :stroke-width 0.03 :stroke "#000000" :fill "white"}]
      [:line {:x1 -0.25 :y1 (- -1 y-offset) :x2 -0.1 :y2 (- -1.02 y-offset) :stroke-width 0.03 :stroke "#000000"}]
      [:line {:x1 0.25 :y1 (- -1 y-offset) :x2 0.1 :y2 (- -1.02 y-offset) :stroke-width 0.03 :stroke "#000000"}]
      [:rect {:x -0.5 :y 0 :width 1 :height 0.2 :rx 0.2 :ry 0.2 :fill "rgba(0,0,0,0.2)"}]]))})

(defn game-page [db]
  [:div
   [:div#bg {:style {:height (/ (get-in @db [:window 1]) 2)
                     :background-color "rgba(70,70,70,1)"}}]
   [:svg#canvas {:width (get-in @db [:window 0])
                 :height (get-in @db [:window 1])
                 :viewBox "-10.5 -6.75 21 9"}
    [:polygon {:points "-10.5,2.25 -6.93,-2.25 6.93,-2.25 10.5,2.25"
               :fill "rgba(0,0,0,0.3)"
               :on-click #(js/console.log "clicked!")}]
    (doall (for [[id e] (get @db :entities)]
             (with-meta [(get renderers (e :renderer)) @db id e] {:key id})))]])

(defn about-page [db]
  [:div [:h2 "About vecscape-client"]
   [:div [:a {:href "/"} "go to the home page"]]])

(defn current-page [db]
  [:div [(session/get :current-page) db]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'game-page))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (let [db (reagent/atom (make-initial-db {:window [] :entities {}}))
        resize-handler! (partial event-resize (reagent/cursor db [:window]))]
    (resize-handler! nil)
    (.addEventListener js/window "resize" resize-handler!)
    (add-entity! db "a" {:pos [0 0] :renderer :player :created (now)})
    (launch-game-loop! db)
    (reagent/render [current-page db] (.getElementById js/document "app"))))

(defn init! []
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
