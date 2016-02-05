(ns clj-refactor.edit
 (:require
   [clojure.string :as string]
   [clojure.zip :as cz]
   [rewrite-clj.node :as n]
   [rewrite-clj.node.forms :as nf]
   [rewrite-clj.node.protocols :as np]
   [rewrite-clj.paredit :as p]
   [rewrite-clj.zip :as z]
   [rewrite-clj.zip.utils :as zu]
   [rewrite-clj.zip.whitespace :as ws]))

(defn top? [loc]
  (= nf/FormsNode (type (z/node loc))))

(defn zdbg [loc msg]
  (doto (z/sexpr loc) (prn msg))
  loc)

(defn exec-to [loc f p?]
  (->> loc
       (iterate f)
       (take-while p?)
       last))

(defn to-root [loc]
  (exec-to loc z/up #(not (top? %))))

(defn parent-let? [zloc]
  (= 'let (-> zloc z/up z/leftmost z/sexpr)))

(defn find-op
  [zloc]
  (if (z/seq? zloc)
    (z/down zloc)
    (z/leftmost zloc)))

(defn find-ops-up
  [zloc & op-syms]
  (let [oploc (find-op zloc)]
    (if (contains? (set op-syms) (z/sexpr oploc))
      oploc
      (let [next-op (z/leftmost (z/up oploc))]
        (when-not (= next-op zloc)
          (apply find-ops-up next-op op-syms))))))

(defn single-child?
  [zloc]
  (let [child (z/down zloc)]
    (and (z/leftmost? child)
         (z/rightmost? child))))

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

(defn remove-all-after
  [zloc]
  (loop [zloc (zu/remove-right-while zloc (constantly true))]
    (if-let [uploc (z/up zloc)]
      (recur (zu/remove-right-while uploc (constantly true)))
      zloc)))

(defn read-position
  [old-pos zloc]
  (-> zloc
      (zdbg "read-position")
      (remove-all-after)
      (z/root-string)
      (z/of-string)
      (z/rightmost)
      (z/find-next-depth-first (comp z/end? z/next))
      (z/node)
      (meta)
      ((juxt :row :col))))

(defn mark-position
  [zloc marker]
  (z/replace zloc (assoc (z/node zloc) ::marker marker)))

(defn find-mark
  [zloc marker]
  (if-let [mloc (z/find (to-root zloc) z/next #(= marker (get (z/node %) ::marker)))]
    mloc
    zloc))
