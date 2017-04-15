(ns vecscape-client.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [vecscape-client.data :refer [make-initial-db]]))

(def ASPECT [21 9])

;; -------------------------
;; Functions

(defn rectangle-in-rectangle [r1 r2]
  (let [scale (js/Math.min (/ (first r2) (first r1)) (/ (last r2) (last r1)))]
    (map #(int (* % scale)) r1)))

;; -------------------------
;; Events

(defn event-resize [c ev]
  (reset! c
          (vec (rectangle-in-rectangle [21 9] [(.-innerWidth js/window) (.-innerHeight js/window)]))))

;; -------------------------
;; Views

(defn game-page [db]
  [:div
   [:div#bg {:style {:height (/ (get-in @db [:window 1]) 2)
                     :background-color "rgba(70,70,70,1)"}}]
   [:svg#canvas {:width (get-in @db [:window 0])
                 :height (get-in @db [:window 1])
                 :viewBox "0 0 21 9"}
    [:circle {:r 0.5 :cx 10.5 :cy 5.5 :stroke-width 0.03 :stroke "#000000" :fill "white"}]
    [:rect {:x 10 :y 6.5 :width 1 :height 0.2 :rx 0.2 :ry 0.2 :fill "rgba(0,0,0,0.2)"}]]])

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
  (let [db (reagent/atom (make-initial-db {:window []}))
        resize-handler! (partial event-resize (reagent/cursor db [:window]))]
    (resize-handler! nil)
    (.addEventListener js/window "resize" resize-handler!)
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
