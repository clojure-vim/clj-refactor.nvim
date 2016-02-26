(ns clj-refactor.transform
  (:require
   [clj-refactor.edit :as edit]
   [cljs.nodejs :as nodejs]
   [cljs.reader :as reader]
   [clojure.zip :as zz]
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
          (z/right)
          (z/right)
          (edit/remove-left) ; remove let
          (edit/remove-left) ; remove binding
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

(defn extract-def
  [zloc [def-name]]
  (let [def-sexpr (z/sexpr zloc)
        def-sym (symbol def-name)]
    (-> zloc
        (edit/to-top)
        (edit/mark-position :first-occurrence)
        (edit/replace-all-sexpr def-sexpr def-sym true)
        (edit/find-mark :first-occurrence)
        (z/insert-left (list 'def def-sym def-sexpr)) ; add declare
        (z/insert-left (n/newline-node "\n\n")) ; add new line after location
        (z/left)
        (edit/format-form))))

(defn add-declaration
  "Adds a declaration for the current symbol above the current top level form"
  [zloc _]
  (let [node (z/sexpr zloc)]
    (if (symbol? node)
      (-> zloc
          (edit/to-top)
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
        (z/rightmost) ; Go to last child (else form)
        (edit/transpose-with-left)) ; Swap children
    zloc))

(defn thread-sym
  [zloc sym]
  (let [movement (if (= '-> sym) z/right z/rightmost)]
    (if-let [first-loc (-> zloc (edit/find-op) movement)]
      (let [first-node (z/node first-loc)
            parent-op (z/sexpr (z/leftmost (z/up first-loc)))
            threaded? (= sym parent-op)]
        (-> first-loc
            (z/remove)
            (z/up)
            ((fn [loc] (cond-> loc
                         (edit/single-child? loc) (-> z/down p/raise)
                         (not threaded?) (-> (p/wrap-around :list) (z/insert-left sym)))))
            (z/insert-left first-node)
            (z/insert-left (n/newline-node "\n"))
            (z/leftmost)))
      zloc)))

(defn thread
  [zloc _]
  (thread-sym zloc '->))

(defn thread-last
  [zloc _]
  (thread-sym zloc '->>))

(defn thread-all
  [zloc sym]
  (loop [loc (thread-sym zloc sym)]
    (if (z/seq? (z/right loc))
      (recur (thread-sym (z/right loc) sym))
      loc)))

(defn thread-first-all
  [zloc _]
  (thread-all zloc '->))

(defn thread-last-all
  [zloc _]
  (thread-all zloc '->>))

(defn ensure-list
  [zloc]
  (if (z/seq? zloc)
    (z/down zloc)
    (p/wrap-around zloc :list)))

(defn unwind-thread
  [zloc _]
  (let [oploc (edit/find-op zloc)
        thread-type (z/sexpr oploc)]
    (if (contains? #{'-> '->>} thread-type)
      (let [first-loc (z/right oploc)
            first-node (z/node first-loc)
            move-to-insert-pos (if (= '-> thread-type)
                                 z/leftmost
                                 z/rightmost)]
        (-> first-loc
            (z/right) ; move to form to unwind into
            (edit/remove-left) ; remove threaded form
            (ensure-list) ; make sure we're dealing with a wrapped fn
            (move-to-insert-pos) ; move to pos based on thread type
            (z/insert-right first-node)
            (z/up)
            ((fn [loc]
               (if (z/rightmost? loc)
                 (p/raise loc)
                 loc)))
            (z/up)))
      zloc)))

(defn unwind-all
  [zloc _]
  (loop [loc (unwind-thread zloc nil)]
    (let [oploc (edit/find-op loc)
          thread-type (z/sexpr oploc)]
      (if (contains? #{'-> '->>} thread-type)
        (recur (unwind-thread loc nil))
        loc))))

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
      (z/remove)
      (edit/find-namespace)))

(defn cycle-op
  [zloc a-op b-op]
  (if-let [oploc (edit/find-ops-up zloc a-op b-op)]
    (let [thread-type (z/sexpr oploc)]
      (cond
        (= a-op thread-type) (z/replace oploc b-op)
        (= b-op thread-type) (z/replace oploc a-op)
        :else zloc))
    zloc))

(defn cycle-thread
  [zloc _]
  (cycle-op zloc '-> '->>))

(defn cycle-privacy
  [zloc _]
  (cycle-op zloc 'defn 'defn-))

(defn function-from-example
  [zloc _]
  (let [op-loc (edit/find-op zloc)
        example-loc (z/up (edit/find-op zloc))
        child-sexprs (n/child-sexprs (z/node example-loc))
        fn-name (first child-sexprs)
        args (for [[i arg] (map-indexed vector (rest child-sexprs))]
               (if (symbol? arg)
                 arg
                 (symbol (str "arg" (inc i)))))]
    (-> example-loc
        (edit/to-top)
        (z/insert-left `(~'defn ~fn-name [~@args])) ; add declare
        (z/insert-left (n/newline-node "\n\n"))))) ; add new line after location

(defn extract-function
  [zloc [fn-name used-locals]]
  (let [expr-loc (z/up (edit/find-op zloc))
        expr (z/sexpr expr-loc)
        fn-sym (symbol fn-name)
        used-syms (map symbol used-locals)]
    (-> expr-loc
        (z/replace `(~fn-sym ~@used-syms))
        (edit/mark-position :new-cursor)
        (edit/to-top)
        (z/insert-left `(~'defn ~fn-sym [~@used-syms] ~expr))
        (z/insert-left (n/newline-node "\n\n")))))

(defn format-form
  [zloc _]
  (edit/format-form zloc))

(defn format-all
  [zloc _]
  (edit/format-all zloc))
