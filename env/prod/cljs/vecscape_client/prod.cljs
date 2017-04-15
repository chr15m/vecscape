(ns vecscape-client.prod
  (:require [vecscape-client.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
