(ns uix.css
  (:require [cljs.analyzer.api :as ana-api]
            [cljs.env :as env]
            [cljs.vendor.clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.reader.edn :as edn]
            [uix.css.lib :as css.lib])
  (:import (java.io File)))

(defmacro debug [& body]
  `(binding [*out* *err*]
     ~@body))

(defn compile-rule [class-name selector k v]
  (str (name k) ":"
       (cond
         (and (number? v) (not (css.lib/unitless-prop k)))
         (str v "px")

         (keyword? v)
         (name v)

         (or (symbol? v) (list? v))
         (str "var(--v" (hash (str class-name selector v)) ")")

         :else v)
       ";"))

(defn compile-styles [class-name selector styles]
  (str (str/replace (name selector) "&" (str "." class-name))
       "{"
       (str/join "" (map #(apply compile-rule class-name selector %) styles))
       "}"))

(def styles-reg (atom {}))

(defn styles-by-type [styles]
  (group-by #(let [k (-> % key name)]
               (cond
                 (str/starts-with? k "&") :blocks
                 (str/starts-with? k"@") :media
                 (= "global" k) :global
                 :else :self))
            styles))

(defn walk-styles-compile [class-name styles]
  (let [{:keys [self blocks media global]} (styles-by-type styles)]
    (vec
      (concat
        (->> global
             (map second)
             (apply merge-with merge)
             (mapv (fn [[selector styles]]
                     (compile-styles class-name selector styles))))
        (mapv #(apply compile-styles class-name %) (into [["&" self]] blocks))
        (mapv (fn [[media styles]]
                (str media "{" (compile-styles class-name "&" styles) "}"))
              media)))))

(defn walk-styles [class-name styles f]
  (let [{:keys [self blocks media global]} (styles-by-type styles)]
    (concat
      (mapv #(apply f class-name %) (into [["&" self]] blocks))
      (mapv #(f class-name "&" (last %)) media))))

(def ^:dynamic *build-state*)

(defn release? []
  (-> *build-state*
      :mode
      (= :release)))

(defn root-path []
  (str ".styles/"
       (if (release?) "release" "dev")))

(defn styles-modules []
  (->> (file-seq (io/file (root-path)))
       (filter #(.isFile ^File %))))

(defn write-modules! [styles-reg]
  (doseq [[file styles] styles-reg]
    (let [path (str (root-path) "/" file ".edn")]
      (io/make-parents path)
      (spit path (str styles)))))

(defn write-source-map! [styles css-str output-to]
  (let [file-name (peek (str/split output-to #"/"))
        styles (->> styles
                    (reduce-kv (fn [ret class v]
                                 (assoc ret class (assoc v :css-loc (.indexOf ^String css-str ^String class))))
                               {}))
        sm (->> (vals styles)
                (reduce (fn [ret {:keys [file line column css-loc]}]
                          (assoc-in ret [file (dec line) column] [{:gline 0 :gcol css-loc}]))
                        {}))
        sources (->> (vals styles)
                     (map (fn [{:keys [file]}]
                            [file (-> file io/resource slurp)]))
                     (into #{}))
        sources-content (map second sources)
        source-files (map first sources)
        sm (into (cljs.source-map/encode* sm {:file file-name
                                              :sources-content sources-content})
                 {"sources" source-files})]
    (spit (str output-to ".map") (json/write-str sm :escape-slash false))))

(defn write-bundle! [state {:keys [output-to]}]
  (let [build-sources (->> (:build-sources state)
                           (map second)
                           (filter string?)
                           (into #{}))
        used (->> (styles-modules)
                  (filter #(-> (.getPath ^File %)
                               (str/replace #"^\.styles\/(dev|release)/" "")
                               (str/replace #"\.edn$" "")
                               build-sources)))
        styles (->> used
                    (map (comp edn/read-string slurp))
                    (apply merge)
                    (reduce-kv (fn [ret class styles]
                                 (assoc ret class (->> (walk-styles-compile class (:styles styles))
                                                       (assoc styles :css-str))))
                               {}))
        styles-strs (mapcat :css-str (vals styles))
        file-name (peek (str/split output-to #"/"))
        sm-path (str file-name ".map")
        out (str (str/join "" styles-strs)
                 "\n/*# sourceMappingURL=" sm-path " */")]
    (write-source-map! styles out output-to)
    (spit output-to out)))

(defn write-styles! [state config]
  (binding [*build-state* state]
    (write-modules! @styles-reg)
    (write-bundle! state config)
    (reset! styles-reg {})))

(defn eval-symbol [env v]
  (let [ast (ana-api/resolve env v)]
    (cond
      (and (= :local (:op ast))
           (-> ast :init :op (= :const)))
      (-> ast :init :val)

      (= :var (:op ast))
      (let [ast (ana-api/no-warn
                  (->> ast
                       :root-source-info
                       :source-form
                       (ana-api/analyze env)))]
        (when (-> ast :init :op (= :const))
          (-> ast :init :val)))

      :else ::nothing)))

(def evaluators
  {'cljs.core/inc inc
   'cljs.core/dec dec
   'cljs.core/str str})

(declare eval-css-value)

(defn eval-expr [env [f & args :as expr]]
  (cond
    (symbol? f)
    (if-let [eval-fn (->> f (ana-api/resolve env) :name evaluators)]
      (let [args (map #(eval-css-value env %) args)]
        (when (every? (complement #{::nothing}) args)
          (apply eval-fn args)))
      ::nothing)

    :else ::nothing))

(defn eval-value [env v]
  (ana-api/no-warn
    (let [ast (ana-api/analyze env v)]
      (if (= :const (:op ast))
        (:val ast)
        ::nothing))))

(defn eval-css-value [env v]
  (cond
    (symbol? v) (eval-symbol env v)
    (list? v) (eval-expr env v)
    :else (eval-value env v)))

(defn find-dyn-styles [class-name styles env]
  (let [evaled-styles (atom {})]
    [(->> (walk-styles class-name styles
            (fn [class-name selector styles]
              (->> styles
                   (keep (fn [[k v]]
                           (when (or (symbol? v) (list? v))
                             (let [ret (eval-css-value env v)]
                               (if (= ::nothing ret)
                                 [(str "--v" (hash (str class-name selector v))) `(uix.css.lib/interpret-value ~k ~v)]
                                 (do (swap! evaled-styles assoc k ret)
                                     nil)))))))))
          (mapcat identity)
          (into {}))
     @evaled-styles]))

(def release-counter (atom 0))

(defmacro css [styles]
  (binding [*build-state* (:shadow.build.cljs-bridge/state @env/*compiler*)]
    (let [file (-> &env :ns :meta :file)
          {:keys [line column]} (select-keys &env [:line :column])
          class (if (release?)
                  (str "k" (swap! release-counter inc))
                  (str (-> &env :ns :name (str/replace "." "-"))
                       "-L" line "-C" column))
          [dyn-styles evaled-styles] (find-dyn-styles class styles &env)]
      (swap! styles-reg assoc-in [file class] {:styles (into styles evaled-styles)
                                               :file file
                                               :line line
                                               :column column})
      [class dyn-styles])))

(defn hook
  {:shadow.build/stage :compile-finish}
  [build-state config]
  (write-styles! build-state config)
  build-state)