(ns vecscape-client.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [cljs.core.async :refer [<! alts! put! timeout chan]]
              [vecscape-client.data :refer [make-initial-db]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

; let go loops detect if they're stale
(defonce loop-instance (atom 0))
(swap! loop-instance inc)

(def ASPECT [21 9])

;; -------------------------
;; Functions

(defn update-player! [e]
  (print "update-player!" @e)
  (go (loop [instance-id @loop-instance]
        (let [[[event-type event-message] channel] (alts! [(@e :chan) (timeout 1000)])]
          (if (not= event-type "frame")
            (print "update-player! event" event-type event-message))
          (cond
            (= event-type "frame")
            (swap! e
                   (fn [old-e]
                     (-> old-e
                         (assoc :pos (get old-e :target))
                         (assoc :height (* 0.08 (js/Math.sin (* (old-e :bob) (- (old-e :created-timestamp) event-message))))))))
            (= event-type "target")
            (do
              (swap! e assoc :target event-message)
              (js/console.log event-message))))
        (if (= @loop-instance instance-id) (recur instance-id)))
      (print "Exiting player loop.")))

(defn now []
  (.getTime (js/Date.)))

(defn rectangle-in-rectangle [r1 r2]
  (let [scale (js/Math.min (/ (first r2) (first r1)) (/ (last r2) (last r1)))]
    (map #(int (* % scale)) r1)))

(defn launch-game-event-loop! [db event-chan player]
  (print "Game loop" @loop-instance)
  (go (loop [instance-id @loop-instance]
        (when (= instance-id @loop-instance)
          (let [[[event-type event-message] channel] (alts! [event-chan (timeout 200)])]
            (if event-type
              (print event-type event-message))
            ;(swap! db assoc :timestamp (now))
            (cond (nil? event-type)
                  (let [frame-time (now)]
                    (doall (map (fn [[id e]] (put! (e :chan) ["frame" frame-time])) (get @db :entities))))

                  (= event-type "floor-click")
                  (do
                    (put! (player :chan) ["target" event-message]))))
          (recur instance-id)))
      (print "Exiting old game loop")))

(defn add-entity! [db id e update-loop]
  (swap! db assoc-in [:entities id] e)
  (update-loop (reagent/cursor db [:entities id])))

(defn screen-to-svg-coordinates [[x y]]
  (let [svg (.getElementById js/document "canvas")
        pt (.createSVGPoint svg)]
    
    (set! (.-x pt) x)
    (set! (.-y pt) y)
    (let [cpt (.matrixTransform pt (.inverse (.getScreenCTM svg)))]
      [(.-x cpt) (.-y cpt)])
    ))

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
     [:svg {:key id :overflow "visible" :x (get (e :pos) 0) :y (get (e :pos) 1)}
      [:circle {:r 0.5 :cx 0 :cy (- -0.9 (e :height)) :stroke-width 0.03 :stroke "#000000" :fill "white"}]
      [:line {:x1 -0.25 :y1 (- -1 (e :height)) :x2 -0.1 :y2 (- -1.02 (e :height)) :stroke-width 0.03 :stroke "#000000"}]
      [:line {:x1 0.25 :y1 (- -1 (e :height)) :x2 0.1 :y2 (- -1.02 (e :height)) :stroke-width 0.03 :stroke "#000000"}]
      [:rect {:x -0.5 :y 0 :width 1 :height 0.2 :rx 0.2 :ry 0.2 :fill "rgba(0,0,0,0.2)"}]])})

(defn game-page [db event-chan]
  [:div
   [:div#bg {:style {:height (/ (get-in @db [:window 1]) 2)
                     :background-color "rgba(70,70,70,1)"}}]
   [:svg#canvas {:width (get-in @db [:window 0])
                 :height (get-in @db [:window 1])
                 :viewBox "-10.5 -6.75 21 9"}
    [:polygon {:points "-10.5,2.25 -6.93,-2.25 6.93,-2.25 10.5,2.25"
               :fill "rgba(0,0,0,0.3)"
               :on-click #(put! event-chan ["floor-click" (screen-to-svg-coordinates [(.-clientX %) (.-clientY %)])])}]
    (doall (for [[id e] (get @db :entities)]
             (with-meta [(get renderers (e :renderer)) @db id e] {:key id})))]])

(defn about-page [db event-chan]
  [:div [:h2 "About vecscape-client"]
   [:div [:a {:href "/"} "go to the home page"]]])

(defn current-page [db event-chan]
  [:div [(session/get :current-page) db event-chan]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'game-page))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (print "mount-root")
  (let [db (reagent/atom (make-initial-db {:window [] :entities {}}))
        resize-handler! (partial event-resize (reagent/cursor db [:window]))
        event-chan (chan)]
    (resize-handler! nil)
    (.addEventListener js/window "resize" resize-handler!)
    (add-entity! db "a" {:pos [0 0] :bob 0.002 :renderer :player :created-timestamp (now) :chan (chan)} update-player!)
    (add-entity! db "b" {:pos [2 -1] :bob 0.004 :renderer :player :created-timestamp (now) :chan (chan)} update-player!)
    (launch-game-event-loop! db event-chan (get-in @db [:entities "a"]))
    (reagent/render [current-page db event-chan] (.getElementById js/document "app"))))

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
