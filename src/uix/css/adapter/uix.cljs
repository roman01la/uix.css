(ns uix.css.adapter.uix
  (:require [uix.compiler.alpha]))

(defonce create-element uix.compiler.alpha/create-element)

(set! uix.compiler.alpha/create-element
      (fn [args children]
        (when-let [props (aget args 1)]
          (when (and (uix.compiler.alpha/pojo? props)
                     (some? (.-css ^js props)))
            (let [[class-name dyn-styles] (.-css ^js props)]
              (js-delete props "css")
              (set! (.-className props)
                    (.trim (.join #js [(.-className props) class-name] " ")))
              (set! (.-style props)
                    (js/Object.assign (or (.-style props) #js {})
                                      (uix.compiler.attributes/convert-prop-value-shallow dyn-styles))))))
        (create-element args children)))