(ns ^:figwheel-always clj-refactor.edit-test
  (:require
   [rewrite-clj.paredit :as p]
   [rewrite-clj.zip :as z]
   [rewrite-clj.zip.whitespace :as zw]
   [cljs.nodejs :as nodejs]
   [cljs.test :refer-macros [deftest is testing run-tests are]]
   [clojure.string :as str]
   [clj-refactor.edit :as e]
   [clj-refactor.test-helper :refer [str-zip-to zip-to apply-zip apply-zip-str apply-zip-to]]))

(deftest test-remove-right
  (are [i j] (= i j)
    '(let x) (apply-zip '(let [x (+ 1 1)] x) 'let e/remove-right)

    '(let [x (+ 1 1)]) (apply-zip '(let [x (+ 1 1)] x) '[x (+ 1 1)] e/remove-right)

    '(let) (apply-zip '(let) 'let e/remove-right)

    'x (apply-zip-to '(x y) 'x e/remove-right)
    '(x) (apply-zip '(x y) 'x e/remove-right)

    'y (apply-zip-to '(x y) 'y e/remove-right)
    '(x y) (apply-zip '(x y) 'y e/remove-right)))

(deftest test-remove-left
  (are [i j] (= i j)
    'x (apply-zip-to '(x y) 'x e/remove-left)
    '(x y) (apply-zip '(x y) 'x e/remove-left)

    'y (apply-zip-to '(x y) 'y e/remove-left)
    '(y) (apply-zip '(x y) 'y e/remove-left)))

(deftest test-transpose-with-left
  (are [i j] (= i j)
    '(y x) (apply-zip '(x y) 'y e/transpose-with-left)

    '((a y) (b x)) (apply-zip '((b x) (a y)) '(a y) e/transpose-with-left)

    '(x z y) (apply-zip '(x y z) 'z e/transpose-with-left)

    '(x y z) (apply-zip '(x y z) 'x e/transpose-with-left)

    '(x [a b c] y z) (apply-zip '(x [a b c] y z) 'a e/transpose-with-left)

    '(x [b a c] y z) (apply-zip '(x [a b c] y z) 'b e/transpose-with-left)

    '([a b c] x y z) (apply-zip '(x [a b c] y z) '[a b c] e/transpose-with-left)))

(deftest test-transpose-with-right
  (are [i j] (= i j)
    '(x y) (apply-zip '(x y) 'y e/transpose-with-right)

    '(x y z) (apply-zip '(x y z) 'z e/transpose-with-right)

    '(x z y) (apply-zip '(x y z) 'y e/transpose-with-right)

    '(y x z) (apply-zip '(x y z) 'x e/transpose-with-right)

    '(x [a b c] y z) (apply-zip '(x [a b c] y z) 'c e/transpose-with-right)

    '(x [a c b] y z) (apply-zip '(x [a b c] y z) 'b e/transpose-with-right)

    '(x y [a b c] z) (apply-zip '(x [a b c] y z) '[a b c] e/transpose-with-right)))

(deftest test-marking
  (let [zloc (str-zip-to "(a (b c))\n(x (y z))" 'z identity)]
    (is (= 'z
          (-> zloc
              (e/mark-position ::marked)
              (z/up)
              (z/up)
              (z/insert-left '(x y))
              (z/left)
              (e/find-mark ::marked)
              (z/sexpr))))))

(deftest test-read-position
  (is (= [1 4]
         (str-zip-to "(a (b c))\n(x (y z))" '(b c) #(e/read-position [55 44] % 0))))
  (is (= [2 2]
         (str-zip-to "(a (b c))\n(x (y z))" 'x #(e/read-position [55 44] % 0))))
  (is (= [2 6]
         (str-zip-to "(a (b c)\n  (xero (y z)))" 'xero #(e/read-position [0 5] % 2)))))

(deftest test-format-form
  (are [i j] (= i j)
    "(a\n (b\n  c))" (apply-zip-str "(a\n(b\nc))" 'b e/format-form)
    "(a\n (b\n  c))\n(d\ne)" (apply-zip-str "(a\n(b\nc))\n(d\ne)" 'b e/format-form)

    "(a\n (b\n  c))" (apply-zip-str "(a\n(b\nc))" 'b e/format-all)
    "(a\n (b\n  c))\n(d\n e)" (apply-zip-str "(a\n(b\nc))\n(d\ne)" 'b e/format-all)))
