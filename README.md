# uix.css

[![Clojars Project](https://img.shields.io/clojars/v/com.github.roman01la/uix.css.svg)](https://clojars.org/com.pitch/uix.core)

CSS-in-CLJS library

```clojure
(ns my.app
  (:require [uix.core :as uix :refer [defui $]]
            [uix.css :refer [css]]
            [uix.css.adapter.uix]))

(defn button []
  ($ :button {:css (css {:font-size "14px"
                         :background "#151e2c"})}))
```

## Motivation

I love inline styles, unfortunately they are quite limited, there's no way to specify states (hover, active, etc) or use media queries. Essentially, inline styles won't let you use all of CSS.

`uix.css` is a successor of [cljss](https://github.com/clj-commons/cljss), similar library that I created some years ago.

## Usage

The library relies on shadow-cljs to generate and bundle CSS. `css` macro accepts a map of styles and returns a tuple of a class name and a map of dynamic inline styles.

In the example below I'm using [UIx](https://github.com/pitch-io/uix). The library can be used with any other React wrapper as long as a proper adapter is provided (take a look at `uix.css.adapter.uix` ns to learn how to build adapters). The adapter takes care of applying whatever `css` macro returns.

```clojure
(ns my.app
  (:require [uix.core :as uix :refer [defui $]]
            [uix.css :refer [css]]
            [uix.css.adapter.uix]))

(def border-color "blue")

(defn button []
  ($ :button {:css (css {:font-size "14px"
                         :background "#151e2c"
                         :padding "8px 32px"
                         :border (str "1px solid " border-color)
                         :&:hover {:background "green"}
                         "@media (max-width: 800px)" {:padding "4px 12px"}
                         "& > strong" {:font-weight 600}})}))
```

`uix.css/hook` build hook takes care of generating CSS and creating a bundle. 

```clojure
;; shadow-cljs.edn
{:deps true
 :dev-http {8080 "public"}
 :builds {:website
          {:target :browser
           :build-hooks [(uix.css/hook {:output-to "public/website.css"})]
           :modules {:website {:entries [my.app]}}}}}
```

When compiled, static part of the styles map is dumped into CSS bundle, but dynamic part of it (see `border-color` example above) is deferred to runtime, where values are assigned via CSS Variables API.

## Source maps

While generated class names are quite descriptive (`auix-core-L18-C20` — ns + line + column), we also generate CSS source maps to improve debugging experience.

![](/source_maps.jpg)

## Evaluators

`uix.css` tries to inline constant values and pure expressions to reduce the number of dynamic styles, this is especially usedful when you have a set of shared design tokens in code, like colors, font sizes, spacing, etc.

In this example the value of `border-color` var will be inlined, as well as `(str "1px solid " border-color)` expression. `css` macro analyzes the code and evaluates well known functions given that their arguments are constant values.
```clojure
(def border-color "blue")

(def m-xl 64)

(css {:border (str "1px solid " border-color)
      :margin m-xl})
```