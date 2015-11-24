(ns nvim-refactor.main
  (:require
   [cljs.reader :as reader]
   [clojure.string :as string]
   [clojure.zip :as zz]
   [parinfer.paren-mode :as paren-mode]
   [rewrite-clj.node :as n]
   [rewrite-clj.node.forms :as nf]
   [rewrite-clj.parser :as parser]
   [rewrite-clj.paredit :as p]
   [rewrite-clj.zip :as z]
   [rewrite-clj.zip.base :as zb]
   [rewrite-clj.zip.findz :as zf]
   [rewrite-clj.zip.utils :as zu]
   [rewrite-clj.zip.whitespace :as ws]))

(defonce nconn (atom nil))

(defn jdbg [val]
  (js/debug val)
  val)

(defn cdbg [val]
  (js/debug (pr-str val))
  val)

(defn zdbg [loc]
  (js/debug (pr-str (z/sexpr loc)))
  loc)

(defn split-lines [s]
  (string/split s #"\r?\n" -1))

(defn exec-to [loc f p?]
  (->> loc
       (iterate f)
       (take-while p?)
       last
       f))

(defn parent-let? [zloc]
  (= 'let (-> zloc z/up z/leftmost z/sexpr)))

(defn remove-left [zloc]
  (-> zloc
    (zu/remove-left-while ws/whitespace?)
    (zu/remove-left-while (complement ws/whitespace?))))

(defn remove-right [zloc]
  (-> zloc
    (z/remove)
    (z/next)))

(defn transpose-backwards [zloc]
  (let [n (z/node zloc)]
    (-> zloc
        remove-right
        z/left
        (z/insert-left n)
        z/left)))

;; TODO Is this safe?
(defn join-let
  "if a let is directly above a form, will join binding forms and remove the inner let"
  [let-loc]
  (let [bind-node (z/node (z/next let-loc))]
    (if (parent-let? let-loc)
      (do
       (cdbg "joining")
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
           (z/insert-left (n/newline-node "\n"))
           (z/up) ; move to new binding
           (z/leftmost))) ; move to let
      let-loc)))

;; TODO replace bound forms that are being expanded around
(defn expand-let
  "Expand the scope of the next let up the tree."
  [zloc _]
  ;; TODO check that let is also leftmost?
  (let [let-loc (z/find-value zloc z/prev 'let)
        bind-node (z/node (z/next let-loc))]

    (if (parent-let? let-loc)
      (join-let let-loc)
      (-> let-loc
          (z/up) ; move to form above
          (z/splice) ; splice in let

          (remove-right) ; remove let
          (remove-right) ; remove binding

          (z/leftmost) ; go to front of form above
          (z/up) ; go to form container
          (p/wrap-around :list) ; wrap with new let list
          (z/up) ; move to new let list
          (z/insert-child (n/newline-node "\n")) ; insert let and bindings backwards
          (z/insert-child bind-node)
          (z/insert-child 'let)
          (z/leftmost) ; go to let
          (join-let))))) ; join if let above

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
        (join-let)))) ; join if let above

;; TODO this can probably escape the ns form - need to root the search it somehow (z/.... (z/node zloc))
(defn find-or-create-require [zloc v]
  (if-let [zfound (z/find-next-value zloc z/next v)]
    zfound
    (-> zloc
        (z/append-child (n/newline-node "\n"))
        (z/append-child (list v))
        z/down
        z/rightmost
        z/down)))

;; TODO will insert duplicates
(defn add-candidate
  "Add a lib spec to ns form - missing is the package or class and missing-type is #{:ns :class :type :macro}"
  [zloc [missing missing-type results sym-ns]]
  (try
   (-> zloc
       (z/find z/up #(= nf/FormsNode (type (z/node %)))) ; go to outer form
       (z/find-next-value z/next 'ns) ; go to ns
       (z/up) ; ns form
       (cond->
         (= missing-type :class)
         (->
          (find-or-create-require :import) ; go to import
          (z/insert-right (n/newline-node "\n"))
          (z/insert-right (symbol missing)))  ; add class

         (= missing-type :ns)
         (->
          (find-or-create-require :require) ; go to require
          (z/insert-right (n/newline-node "\n"))
          (z/insert-right [(symbol missing)]) ; add require vec and ns
          (z/right))

         (and sym-ns (= missing-type :ns)) ; if there was a requested ns `str/trim`
         (->
          (z/append-child :as) ; add :as
          (z/append-child (symbol sym-ns))))) ; as prefix
   (catch :default e
     (js/debug "EXCEPTION" e e.stack))))

(declare run-transform)

(defn add-missing-libspec
  "Asks repl for the missing libspec.
  When the repl comes back with response, run transform to add to ns"
  [zloc _ nvim]
  (when-let [conn @nconn]
    (let [sym (z/sexpr zloc)
          sym-ns (namespace sym)
          symstr (str (when sym-ns (str sym-ns "/")) (name sym))]
      (js/debug "sending" (pr-str conn))
      (.send conn #js {:op "resolve-missing" :symbol (cdbg symstr) :debug "true"}
             (fn [err results]
               (try
                (let [cstr (aget (first results) "candidates")
                      candidates (reader/read-string (cdbg cstr))]
                  (cdbg candidates)
                  (when (> (count candidates) 1)
                    (js/debug "More than one candidate!"))
                  (if-let [[missing missing-type] (first candidates)]
                    (run-transform add-candidate nvim [missing missing-type sym-ns] [1 1 1 1])))
                (catch :default e
                  (js/debug "add-missing response exception" e e.stack)))))))
  zloc)


(defn zip-it
  "Finds the loc at row col of the file and runs the transformer-fn."
  [transformer nvim lines row col args]
  (try
    (let [sexpr (string/join "\n" lines)
          pos (cdbg {:row row :col col :end-row row :end-col col})
          new-sexpr (-> sexpr
                        (z/of-string)
                        (z/find-last-by-pos pos #(not= (z/tag %) :whitespace))
                        (transformer args nvim) ;; TODO should check if anything has changed
                        (z/root-string)
                        (paren-mode/format-text)
                        :text)]
      (split-lines new-sexpr))
    (catch :default e
      (js/debug "zip-it" e (.-stack e)))))

(defn run-transform [transformer nvim args [_ row col _]]
  "Reads the current buffer, runs the transformation and modifies the current buffer with the result."
  (try
   (.getCurrentBuffer nvim
                      (fn [err buf]
                        (.getLineSlice buf 0 -1 true true
                                       (fn [err lines]
                                         (when-let [new-lines (clj->js (zip-it transformer nvim (js->clj lines) row col args))]
                                           (.setLineSlice buf 0 -1 true true new-lines))))))
   (catch :default e
     (js/debug "run-transform" e))))

(defn slurp [filename]
  (let [fs (js/require "fs")
        data (.readFileSync fs filename "utf8")]
    data))

(defn file-exists? [filename]
  (let [fs (js/require "fs")]
    (try
      (.accessSync fs filename)
      true
      (catch :default e
        false))))

(defn connect-to-repl [nvim parent-directory]
  (try
    (let [dirs (reductions conj [] (string/split parent-directory #"/" -1))
          directories (reverse (remove #{""} (map (partial string/join "/") dirs)))
          port-files (map #(str % "/.nrepl-port") directories)
          valid-files (filter file-exists? port-files)]
      (when-let [port-file (first valid-files)]
        (let [port (js/parseInt (slurp port-file))
              client (js/require "nrepl-client")
              connection (.connect client #js {:port port})]
         (when-not @nconn
          (.on connection "error"
                        (fn [err]
                          (js/debug "disconnected" err)
                          (reset! nconn nil)))
          (.once connection "connect"
                     (fn []
                       (js/debug "connected" port (pr-str connection))
                       (reset! nconn connection)))))))
    (catch :default e
      (js/debug "EXCEPTION" e))))

(defn -main []
  (try
   (when (exists? js/plugin)
     (js/debug "hello refactor")
     (.autocmd js/plugin "BufEnter" #js {:pattern "*.clj" :eval "expand('%:p:h')"} connect-to-repl)
     (.commandSync js/plugin "CIntroduceLet" #js {:eval "getpos('.')" :nargs 1} (partial run-transform introduce-let))
     (.commandSync js/plugin "CExpandLet" #js {:eval "getpos('.')" :nargs "*"} (partial run-transform expand-let))
     (.commandSync js/plugin "CAddMissingLibSpec" #js {:eval "getpos('.')" :nargs "*"} (partial run-transform add-missing-libspec)))
   (catch js/Error e
     (js/debug "main exception" e))))

(set! *main-cli-fn* -main)
