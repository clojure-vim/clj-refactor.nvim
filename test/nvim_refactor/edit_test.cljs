(ns ^:figwheel-always nvim-refactor.edit-test
  (:require
   [rewrite-clj.zip :as z]
   [cljs.nodejs :as nodejs]
   [cljs.test :refer-macros [deftest is testing run-tests]]
   [clojure.string :as str]
   [nvim-refactor.edit :as e]))

(defn apply-zip [form f]
  (-> form
      (str)
      (z/of-string)
      (f)
      (z/root-string)))

(deftest test-remove-right
  (is (= (str '(let x))
         (apply-zip '(let [x (+ 1 1)] x)
                    (comp
                     e/remove-right
                     z/down))))

  (is (= (str '(let [x (+ 1 1)]))
         (apply-zip '(let [x (+ 1 1)] x)
                    (comp
                     e/remove-right
                     z/rightmost
                     z/down))))

  (is (= (str '())
         (apply-zip '(let)
                    (comp
                     e/remove-right
                     z/down)))))
