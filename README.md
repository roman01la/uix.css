<img src="logo.svg" width="128" />

[![Clojars Project](https://img.shields.io/clojars/v/com.github.roman01la/uix.css.svg)](https://clojars.org/com.github.roman01la/uix.css)

CSS-in-CLJS library

```clojure
(ns my.app
  (:require [uix.core :as uix :refer [defui $]]
            [uix.css :refer [css]]
            [uix.css.adapter.uix]))

(defn button []
  ($ :button {:style (css {:font-size "14px"
                           :background "#151e2c"})}))
```

- Discuss at #uix on Clojurians Slack

## Installation

```clojure
{:deps {com.github.roman01la/uix.css {:mvn/version "0.3.0"}}}
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
  ($ :button {:style (css {:font-size "14px"
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
           :build-hooks [(uix.css/hook)]
           :modules {:website {:entries [my.app]}}}}}
```

When compiled, static part of the styles map is dumped into CSS bundle, but dynamic part of it (see `border-color` example above) is deferred to runtime, where values are assigned via CSS Variables API.

### Styles composition

`css` macro takes arbitrary number of styles

```clojure
(defui button [{:keys [style children]}]
  ($ :button
    {:style (css {:color :red
                  :padding "8px 16px"}
                 style)}
    children))

($ button {:style (css {:background :yellow})}
   "press me")
```

When a map of styles is passed at runtime, it will be applied as normal inline styles. This behaviour exists specifically for a case when you have UI styled with inline CSS and want to migrate to `uix.css` gradually.

In this example existing `button` component was updated with `css` macro, but all usage places are still passing inline styles, meaning that updating internals of the component won't break its users. 

```clojure
(defui button [{:keys [style children]}]
  ($ :button
    {:style (css {:color :red
                  :padding "8px 16px"}
                 style)}
    children))

;; these styles will be applied as inline styles
($ button {:style {:background :yellow}}
   "press me")
```

### Global styles

Styles passed under `:global` keyword are not scoped to current element, also global styles do not support dynamic values. This exists as a convenience, to avoid creating CSS file just for global styles.

```clojure
(defui app []
  ($ :div {:style (css {:width "100vw"
                        :min-height "100vh"
                        :background "#10121e"
                        :color "#d7dbf1"
                        :global {:html {:box-sizing :border-box}
                                 "html *" {:box-sizing :inherit}
                                 :body {:-webkit-font-smoothing :antialiased
                                        :-moz-osx-font-smoothing :grayscale
                                        :-moz-font-feature-settings "\"liga\" on"
                                        :text-rendering :optimizelegibility
                                        :margin 0
                                        :font "400 16px / 1.4 Inter, sans-serif"}}})}))
```

## Source maps

While generated class names are quite descriptive (`auix-core-L18-C20` — ns + line + column), we also generate CSS source maps to improve debugging experience.

![](/source_maps.jpg)

## Evaluators

`uix.css` tries to inline constant values and pure expressions to reduce the number of dynamic styles, this is especially useful when you have a set of shared design tokens in code, like colors, font sizes, spacing, etc.

In this example the value of `border-color` var will be inlined, as well as `(str "1px solid " border-color)` expression. `css` macro analyzes the code and evaluates well known functions given that their arguments are constant values.
```clojure
(def border-color "blue")

(def m-xl 64)

(css {:border (str "1px solid " border-color)
      :margin m-xl})
```

## Code-splitting

Starting from v0.3.0 uix.css follows shadow's code splitting via modules. Here's UIx example:

```clojure
;; main module
(ns app.core
  (:require [uix.core :as uix :refer [$ defui]]
            [uix.css :refer [css]]
            [uix.css.adapter.uix]
            [shadow.lazy]))

;; create loadable var
(def loadable-settings
  (shadow.lazy/loadable app.settings/view))

(def settings
  ;; creates lazy React component
  (uix.core/lazy
    ;; loads CSS bundle of the settings module
    #(uix.css/load-before app.settings
       ;; loads settings module
       (shadow.lazy/load loadable-settings))))

(defui root-layout [] 
  ($ :div {:style (css {:padding 24})}
    ;; Suspense component displays the fallback UI while
    ;; lazy component is being loaded
    ($ uix.core/suspense {:fallback "loading settings..."}
      ($ settings))))

(defn init []
  ;; render
  )

;; settings module
(ns app.settings
  (:require [uix.core :as uix :refer [$ defui]]
            [uix.css :refer [css]]
            [uix.css.adapter.uix]))

(defui view []
  ($ :div {:style (css {:padding 16})}))

;; shadow-cljs.edn build config
{:app {:target :browser
       :module-loader true
       :modules {:main {:entries [app.core]
                        :init-fn app.core/init}
                 :settings {:entries [app.settings]
                            :depends-on #{:main}}}
       :build-hooks [(uix.css/hook)]}}
```

Building this example will output two CSS bundles next to JavaScript bundles: `main.css` and `settings.css`.

Same as for splitted JavaScript, you have to load initial CSS bundle explicitly, by declaring it via `<link>` element in HTML. But for dynamically loaded modules you need to use `uix.css/load-before` function to load CSS bundle of the specified module before the module itself.

## TODO
- [ ] Pluggable CSS linting
- [ ] Server-side rendering on JVM
