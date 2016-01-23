(ns ^:figwheel-always nvim-refactor.main-test
    (:require [cljs.nodejs :as nodejs]
              [cljs.test :refer-macros [deftest is testing run-tests]]
              [clojure.string :as str]
              [nvim-refactor.main :as m]
              [nvim-refactor.edit :as e]))

(nodejs/enable-util-print!)
(def -main (fn [] nil))
(set! *main-cli-fn* -main)

(defn j [s]
  (str/join "\n" s))

(deftest testing-introduce-let
  (is (= (j ["(defn [bar]"
             "  (let [x (+ 1 1)"
             "        foo x]"
             "    foo))"])

         (j (m/zip-it m/introduce-let
                      ["(defn [bar]"
                       "  (let [x (+ 1 1)]"
                       "    x))"]
                      3 5
                      ["foo"])))))

(deftest testing-expand-let
  (is (= (j ["(defn [bar]"
             "  (let [y (+ 2 2)"
             "        x (+ 1 1)]"
             "    x))"])
         (j (m/zip-it m/expand-let
                      ["(defn [bar]"
                       "  (let [y (+ 2 2)]"
                       "    (let [x (+ 1 1)]"
                       "      x)))"]
                      4 7
                      [])))))

(defn test-it []
  (run-tests 'nvim-refactor.edit-test
             'nvim-refactor.main-test))
