(ns ^:figwheel-always nvim-refactor.transform-test
    (:require [cljs.nodejs :as nodejs]
              [cljs.test :refer-macros [deftest is testing run-tests]]
              [clojure.string :as str]
              [nvim-refactor.main :as m]
              [nvim-refactor.transform :as t]))

(defn j [s]
  (str/join "\n" s))

(deftest testing-introduce-let
  (is (= (j ["(defn [bar]"
             "  (let [x (+ 1 1)"
             "        foo x]"
             "    foo))"])

         (j (m/zip-it t/introduce-let
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
         (j (m/zip-it t/expand-let
                      ["(defn [bar]"
                       "  (let [y (+ 2 2)]"
                       "    (let [x (+ 1 1)]"
                       "      x)))"]
                      4 7
                      [])))))

