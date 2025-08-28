(ns uix.css
  (:require-macros [uix.css])
  (:require [uix.css.lib]
            [goog.style])
  (:import [goog.html SafeStyleSheet]
           [goog.string Const]))

(defn load-stylesheet [path]
  (js/Promise.resolve
    (when-not (js/document.querySelector (str "link[href*='" path "']"))
      (let [el (js/document.createElement "link")]
        (set! (.-rel el) "stylesheet")
        (set! (.-href el) path)
        (js/document.head.append el)))))