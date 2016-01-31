(ns nvim-refactor.transform-test
    (:require [cljs.nodejs :as nodejs]
              [cljs.test :refer-macros [deftest is testing run-tests are]]
              [clojure.string :as str]
              [nvim-refactor.main :as m]
              [nvim-refactor.transform :as t]
              [nvim-refactor.test-helper :refer [apply-zip apply-zip-to]]))

(deftest testing-introduce-let
  (are [i j] (= i j)
    '(defn [bar]
       (let [x (+ 1 1)
             foo (- 1 x)]
         foo))
    (apply-zip
     '(defn [bar]
        (let [x (+ 1 1)]
          (- 1 x)))
     '(- 1 x)
     #(t/introduce-let % ["foo"]))))

(deftest testing-expand-let
  (are [i j] (= i j)
    '(defn [bar]
       (let [y (+ 2 2)
             x (+ 1 1)]
         (- x)))
    (apply-zip
     '(defn [bar]
       (let [y (+ 2 2)]
         (let [x (+ 1 1)]
           (- x))))
     '(- x)
     t/expand-let)))

(deftest testing-cycle-if
  (are [i j] (= i j)
    '(if-not a c b) (apply-zip '(if a b c) 'if t/cycle-if)
    '(if a c b) (apply-zip '(if-not a b c) 'if-not t/cycle-if)
    '(if (pred a) (foo c) (wat b)) (apply-zip '(if-not (pred a) (wat b) (foo c)) 'if-not t/cycle-if)))

(deftest testing-cycle-coll
  (are [i j] (= i j)
    '{a b} (apply-zip '(a b) '(a b) t/cycle-coll)
    '[a b] (apply-zip '{a b} '{a b} t/cycle-coll)
    '#{a b} (apply-zip '[a b] '[a b] t/cycle-coll)
    '(a b) (apply-zip '#{a b} '#{a b} t/cycle-coll)))
