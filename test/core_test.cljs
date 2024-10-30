(ns core-test
  (:require [uix.css :refer [css]]))

(def border-color "#000")
(def hover-bg "yellow")
(def v (atom 90))

(def styles
  (let [p-xl 32]
    (css {:margin 64
          :padding p-xl
          :border (str "1px solid " border-color)
          :&:hover {:color :blue
                    :background hover-bg
                    :width @v}
          "&:hover > div" {:border-radius p-xl}
          "@media (max-width: 800px)" {:color hover-bg
                                       :width (+ 8 9)
                                       :&:hover {:color hover-bg
                                                 :width (+ @v 89)}}})))