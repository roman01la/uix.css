(ns core-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [shadow.cljs.devtools.cli]))

(def css-out
  (->> [".core-test-10-10{margin:64px;padding:32px;border:1px solid #000;}"
        ".core-test-10-10:hover{color:blue;background:yellow;width:var(--core-test-15-29);}"
        ".core-test-10-10:hover > div{border-radius:32px;}"
        "@media (max-width: 800px){.core-test-10-10{color:yellow;width:17px;}.core-test-10-10:hover{color:yellow;width:var(--core-test-20-57);}}"
        "\n"
        "/*# sourceMappingURL=out.css.map */"]
       (str/join "")))

(deftest test-css-compilation
  (testing "generated CSS shoud match snapshot"
    (shadow.cljs.devtools.cli/-main "compile" "test")
    (is (= css-out (slurp "out.css")))
    (.delete (io/file "out.css"))
    (.delete (io/file "out.css.map"))
    (run! io/delete-file (reverse (file-seq (io/file ".styles"))))
    (run! io/delete-file (reverse (file-seq (io/file ".shadow-cljs"))))
    (run! io/delete-file (reverse (file-seq (io/file "public"))))))

(defn -main [& args]
  (run-tests 'core-test))