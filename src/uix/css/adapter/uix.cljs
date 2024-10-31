(ns uix.css.adapter.uix
  (:require [uix.compiler.alpha]
            [uix.compiler.attributes :as uix.attrs]))

;; uix.css adapter
;; 1. Intercepts element creation
;; 2. Extracts `uixCssClass` and `uixCssVars` fields from `props.style`
;; 3. Merges generated class name with the rest of class names in `props.className` field
;; 4. Merges generated CSS Vars map with the rest of styles in `props.style` object

(defonce create-element uix.compiler.alpha/create-element)

(set! uix.compiler.alpha/create-element
      (fn [args children]
        (when-let [^js props (aget args 1)]
          (when (and (uix.compiler.alpha/pojo? props)
                     (js/Array.isArray (.-style props)))
            (let [class-names #js []
                  inline-styles (atom {})]
              (doseq [style (.-style props)]
                (if (:uixCss style)
                  (let [{:keys [class vars]} (:uixCss style)]
                    (.push class-names class)
                    (swap! inline-styles into vars))
                  (swap! inline-styles into style)))
              (js-delete (.-style props) "uixCss")
              (set! (.-className props)
                    (uix.attrs/class-names (.-className props) class-names))
              (set! (.-style props)
                    (js/Object.assign (or (.-style props) #js {})
                                      (uix.compiler.attributes/convert-prop-value-shallow @inline-styles))))))
        (create-element args children)))