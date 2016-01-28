(ns ^:figwheel-always nvim-refactor.edit-test
  (:require
   [rewrite-clj.paredit :as p]
   [rewrite-clj.zip :as z]
   [cljs.nodejs :as nodejs]
   [cljs.test :refer-macros [deftest is testing run-tests are]]
   [clojure.string :as str]
   [nvim-refactor.edit :as e]))

(defn zip-to
  [form goto f]
  (-> form
      (str)
      (z/of-string)
      (z/find z/next #(= (z/sexpr %) goto))
      (f)))

(defn apply-zip-to
  [form goto f]
  (-> form
      (zip-to goto f)
      (z/sexpr)
      (str)))

(defn apply-zip
  [form goto f]
  (-> form
      (zip-to goto f)
      (z/root-string)))

(deftest test-remove-right
  (are [i j] (= (str i) j)
    '(let x) (apply-zip '(let [x (+ 1 1)] x) 'let e/remove-right)

    '(let [x (+ 1 1)]) (apply-zip '(let [x (+ 1 1)] x) '[x (+ 1 1)] e/remove-right)

    '(let) (apply-zip '(let) 'let e/remove-right)

    'x (apply-zip-to '(x y) 'x e/remove-right)))


(deftest test-transpose-with-left
  (are [i j] (= (str i) j)
    '(y x) (apply-zip '(x y) 'y e/transpose-with-left)

    '(x z y) (apply-zip '(x y z) 'z e/transpose-with-left)

    '(x y z) (apply-zip '(x y z) 'x e/transpose-with-left)

    '(x [a b c] y z) (apply-zip '(x [a b c] y z) 'a e/transpose-with-left)

    '(x [b a c] y z) (apply-zip '(x [a b c] y z) 'b e/transpose-with-left)

    '([a b c] x y z) (apply-zip '(x [a b c] y z) '[a b c] e/transpose-with-left)))

(deftest test-transpose-with-right
  (are [i j] (= (str i) j)
    '(x y) (apply-zip '(x y) 'y e/transpose-with-right)

    '(x y z) (apply-zip '(x y z) 'z e/transpose-with-right)

    '(x z y) (apply-zip '(x y z) 'y e/transpose-with-right)

    '(y x z) (apply-zip '(x y z) 'x e/transpose-with-right)

    '(x [a b c] y z) (apply-zip '(x [a b c] y z) 'c e/transpose-with-right)

    '(x [a c b] y z) (apply-zip '(x [a b c] y z) 'b e/transpose-with-right)

    '(x y [a b c] z) (apply-zip '(x [a b c] y z) '[a b c] e/transpose-with-right)))
