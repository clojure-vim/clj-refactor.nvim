(ns clj-refactor.edit
 (:require
   [rewrite-clj.node :as n]
   [rewrite-clj.node.forms :as nf]
   [rewrite-clj.paredit :as p]
   [rewrite-clj.zip :as z]
   [rewrite-clj.zip.utils :as zu]
   [rewrite-clj.zip.whitespace :as ws]))

(defn top? [loc]
  (= nf/FormsNode (type (z/node loc))))

(defn zdbg [loc]
  (doto (z/sexpr loc) prn)
  loc)

(defn exec-to [loc f p?]
  (->> loc
       (iterate f)
       (take-while p?)
       last))

(defn parent-let? [zloc]
  (= 'let (-> zloc z/up z/leftmost z/sexpr)))

(defn find-op
  [zloc]
  (if (z/seq? zloc)
    (z/down zloc)
    (z/leftmost zloc)))

;; TODO Is this safe?
(defn join-let
  "if a let is directly above a form, will join binding forms and remove the inner let"
  [let-loc]
  (let [bind-node (z/node (z/next let-loc))]
    (if (parent-let? let-loc)
      (do
       (-> let-loc
           (z/right) ; move to inner binding
           (z/right) ; move to inner body
           (p/splice-killing-backward) ; splice into parent let
           (z/leftmost) ; move to let
           (z/right) ; move to parent binding
           (z/append-child bind-node) ; place into binding
           (z/down) ; move into binding
           (z/rightmost) ; move to nested binding
           (z/splice) ; remove nesting
           (z/left)
           (ws/append-newline)
           (z/up) ; move to new binding
           (z/leftmost))) ; move to let
      let-loc)))

(defn remove-right [zloc]
  (-> zloc
    (zu/remove-right-while ws/whitespace?)
    (zu/remove-right-while (complement ws/whitespace?))))

(defn remove-left [zloc]
  (-> zloc
    (zu/remove-left-while ws/whitespace?)
    (zu/remove-left-while (complement ws/whitespace?))))

(defn transpose-with-right
  [zloc]
  (if (z/rightmost? zloc)
    zloc
    (let [right-node (z/node (z/right zloc))]
      (-> zloc
          (remove-right)
          (z/insert-left right-node)))))

(defn transpose-with-left
  [zloc]
  (if (z/leftmost? zloc)
    zloc
    (let [left-node (z/node (z/left zloc))]
      (-> zloc
          (z/left)
          (transpose-with-right)))))

(defn find-namespace [zloc]
  (-> zloc
      (z/find z/up top?) ; go to outer form
      (z/find-next-value z/next 'ns) ; go to ns
      (z/up))) ; ns form

;; TODO this can probably escape the ns form - need to root the search it somehow (z/.... (z/node zloc))
(defn find-or-create-libspec [zloc v]
  (if-let [zfound (z/find-next-value zloc z/next v)]
    zfound
    (-> zloc
        (z/append-child (n/newline-node "\n"))
        (z/append-child (list v))
        z/down
        z/rightmost
        z/down
        z/down)))
