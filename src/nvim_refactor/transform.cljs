(ns nvim-refactor.transform
  (:require
   [cljs.nodejs :as nodejs]
   [cljs.reader :as reader]
   [clojure.zip :as zz]
   [nvim-refactor.edit :as edit]
   [parinfer :as parinfer]
   [clojure.string :as string]
   [rewrite-clj.node :as n]
   [rewrite-clj.node.forms :as nf]
   [rewrite-clj.parser :as parser]
   [rewrite-clj.paredit :as p]
   [rewrite-clj.zip :as z]
   [rewrite-clj.zip.base :as zb]
   [rewrite-clj.zip.findz :as zf]
   [rewrite-clj.zip.removez :as zr]
   [rewrite-clj.zip.utils :as zu]
   [rewrite-clj.zip.whitespace :as ws]))

(defn introduce-let
  "Adds a let around the current form."
  [zloc [binding-name]]
  (let [sym (symbol binding-name)]
    (-> zloc
        (p/wrap-around :list) ; wrap with new let list
        (z/up) ; move to new let list
        (z/insert-child 'let) ; add let
        (z/append-child (n/newline-node "\n")) ; add new line after location
        (z/append-child sym) ; add new symbol to body of let
        (z/down) ; enter let list
        (z/next) ; skip 'let
        (p/wrap-around :vector) ; wrap binding vec around form
        (z/up) ; go to vector
        (z/insert-child sym) ; add new symbol as binding
        (z/leftmost) ; back to let
        (edit/join-let)))) ; join if let above

;; TODO replace bound forms that are being expanded around
(defn expand-let
  "Expand the scope of the next let up the tree."
  [zloc _]
  ;; TODO check that let is also leftmost?
  (let [let-loc (z/find-value zloc z/prev 'let)
        bind-node (z/node (z/next let-loc))]

    (if (edit/parent-let? let-loc)
      (edit/join-let let-loc)
      (-> let-loc
          (z/up) ; move to form above
          (z/splice) ; splice in let
          (edit/remove-right) ; remove let
          (edit/remove-right) ; remove binding
          (z/leftmost) ; go to front of form above
          (z/up) ; go to form container
          (p/wrap-around :list) ; wrap with new let list
          (z/up) ; move to new let list
          (z/insert-child (n/newline-node "\n")) ; insert let and bindings backwards
          (z/insert-child bind-node)
          (z/insert-child 'let)
          (z/leftmost) ; go to let
          (edit/join-let))))) ; join if let above

(defn move-to-let
  "Adds form and symbol to a let further up the tree"
  [zloc [binding-name]]
  (let [bound-node (z/node zloc)
        binding-sym (symbol binding-name)]
    (if-let [let-loc (z/find-value zloc z/prev 'let)] ; find first ancestor let
      (-> zloc
          (z/remove) ; remove bound-node and newline
          (ws/append-newline) ; newline to be placed after binding-symbol
          (z/insert-right binding-sym) ; replace it with binding-symbol
          (z/find-value z/prev 'let) ; move to ancestor let
          (z/next) ; move to binding
          (z/append-child (n/newline-node "\n")) ; insert let and bindings backwards
          (z/append-child binding-sym) ; add binding symbol
          (z/append-child bound-node)) ; readd bound node into let bindings
      zloc)))

(defn add-declaration
  "Adds a declaration for the current symbol above the current top level form"
  [zloc _]
  (let [node (z/sexpr zloc)]
    (if (symbol? node)
      (-> zloc
          (edit/exec-to z/up #(not (edit/top? %))) ; Go to top level form
          (z/insert-left (list 'declare node)) ; add declare
          (z/insert-left (n/newline-node "\n\n"))) ; add new line after location
      zloc)))

(defn cycle-coll
  "Cycles collection between vector, list, map and set"
  [zloc _]
  (let [sexpr (z/sexpr zloc)]
    (if (coll? sexpr)
      (let [node (z/node zloc)
            coerce-to-next (fn [sexpr children]
                             (cond
                              (map? sexpr) (n/vector-node children)
                              (vector? sexpr) (n/set-node children)
                              (set? sexpr) (n/list-node children)
                              (list? sexpr) (n/map-node children)))]
        (-> zloc
            (z/insert-right (coerce-to-next sexpr (n/children node)))
            (z/remove)))
      zloc)))

(defn cycle-if
  "Cycles between if and if-not form"
  [zloc _]
  (if-let [if-loc (z/find-value zloc z/prev #{'if 'if-not})] ; find first ancestor if
    (-> if-loc
        (z/insert-left (if (= 'if (z/sexpr if-loc)) 'if-not 'if)) ; add inverse if / if-not
        (z/remove) ; remove original if/if-not
        (z/rightmost) ; Go to last child (true form)
        (edit/transpose-with-left)) ; Swap children
    zloc))

(defn thread
  [zloc _]
  (if-let [first-loc (if (z/seq? zloc)
                       (-> zloc (z/down) (z/right))
                       (-> zloc (z/leftmost) (z/right)))]
    (let [first-node (z/node first-loc)
          parent-op (z/sexpr (z/leftmost (z/up first-loc)))
          threaded? (= '-> parent-op)]
        (js/debug (pr-str parent-op))
        (-> first-loc
            (z/remove)
            (z/up)
            ((fn [loc] (if threaded?
                         loc
                         (-> loc (p/wrap-around :list) (z/insert-left '->)))))
            (z/insert-left first-node)))
    zloc))

;; TODO will insert duplicates
;; TODO handle :type and :macro
(defn add-candidate
  "Add a lib spec to ns form - `missing` is the package or class and `missing-type` is one of `#{:ns :class :type :macro}`"
  [zloc [missing missing-type sym-ns]]
  (-> zloc
      (edit/find-namespace)
      (cond->
        (= missing-type :class)
        (->
         (edit/find-or-create-libspec :import) ; go to import
         (z/insert-right (n/newline-node "\n"))
         (z/insert-right (symbol missing)))  ; add class

        (= missing-type :ns)
        (->
         (edit/find-or-create-libspec :require) ; go to require
         (z/insert-right (n/newline-node "\n"))
         (z/insert-right [(symbol missing)]) ; add require vec and ns
         (z/right))

        (and sym-ns (= missing-type :ns)) ; if there was a requested ns `str/trim`
        (->
         (z/append-child :as) ; add :as
         (z/append-child (symbol sym-ns)))))) ; as prefix

(defn replace-ns
  [zloc [new-ns]]
  (-> zloc
      (edit/find-namespace)
      (z/insert-right new-ns)
      (z/remove)))

