(ns uix.css.adapter.reagent
  (:require [reagent.impl.template :as tmpl]))

(defonce make-element tmpl/make-element)

(set! tmpl/make-element
      (fn [this argv component jsprops first-child]
        (when (js/Array.isArray (.-style jsprops))
          (let [class-names (atom [])
                inline-styles (atom {})]
            (doseq [style (.-style jsprops)]
              (if (:uixCss style)
                (let [{:keys [class vars]} (:uixCss style)]
                  (swap! class-names conj class)
                  (swap! inline-styles into vars))
                (swap! inline-styles into style)))
            (set! (.-className jsprops)
                  (reagent.core/class-names (.-className jsprops) @class-names))
            (if (empty? @inline-styles)
              (js-delete jsprops "style")
              (set! (.-style jsprops)
                    (tmpl/convert-props @inline-styles #js {})))))
        (make-element this argv component jsprops first-child)))
