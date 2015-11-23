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

(defn join-let [let-loc]
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

(defn expand-let [zloc _]
  ;; TODO check leftmost?
  (let [let-loc (z/find-value zloc z/prev 'let)
        bind-node (z/node (z/next let-loc))]

    (if (parent-let? let-loc)
      (join-let let-loc)
      (-> let-loc
          (p/slurp-forward-fully)
          (p/slurp-backward-fully)
          (remove-right) ; remove let
          (remove-right) ; remove binding

          (z/leftmost)
          (z/prev)
          (z/insert-left 'let)
          (z/insert-left bind-node)
          (z/insert-left (n/newline-node "\n"))

          (z/leftmost)
          (join-let)))))

(defn introduce-let [zloc [binding-name]]
  (let [sym (symbol binding-name)]
    (-> zloc
        (p/wrap-around :list)
        (z/up)
        (z/insert-child 'let)
        (z/append-child (n/newline-node "\n"))
        (z/append-child sym)
        (z/down)
        (z/next)
        (p/wrap-around :vector)
        (z/up)
        (z/insert-child sym)
        (z/leftmost) ; back to let
        (join-let))))

(defn zfind
  ([zloc f p?]
   (->> zloc
        (iterate f)
        (take-while identity)
        (drop-while (complement p?))
        (first))))

(defn add-candidate [zloc [err results sym-ns]]
  (try
   (let [cstr (aget (first results) "candidates")
         candidates (reader/read-string (cdbg cstr))]
     (cdbg candidates)
     (when (> (count candidates) 1)
       (js/debug "More than one candidate!"))
     (if-let [[missing missing-type] (first candidates)]
       (-> zloc
           (z/find z/up #(= nf/FormsNode (type (z/node %)))) ; go to outer form
           (z/find-next-value z/next 'ns) ; go to ns
           (z/find-next-value z/next :require) ; go to require
           (z/insert-right (n/newline-node "\n"))
           (z/insert-right []) ; add require vec
           (z/right)
           (z/append-child (symbol missing))
           (z/append-child :as)
           (z/append-child (symbol sym-ns))
           (z/up))
       zloc))
   (catch :default e
     (js/debug "EXCEPTION" e))))

(declare run-transform)

(defn add-missing-libspec [zloc _ nvim]
  (when-let [conn @nconn]
    (let [sym (z/sexpr zloc)
          sym-ns (namespace sym)
          symstr (cdbg (str (when sym-ns (str sym-ns "/")) (name sym)))]
      (cdbg "sending")
      (.send conn #js {:op "resolve-missing" :symbol (cdbg symstr)}
             (fn [err results]
               (run-transform add-candidate nvim [err results sym-ns] [1 1 1 1])))))
  zloc)


(defn zip-it [transformer nvim lines [_ row col _] args]
  (try
    (let [sexpr (string/join "\n" (js->clj lines))
          pos (cdbg {:row row :col col :end-row row :end-col col})
          new-sexpr (-> sexpr
                        (z/of-string)
                        (z/find-last-by-pos pos #(not= (z/tag %) :whitespace))
                        (transformer args nvim)
                        (z/root-string)
                        (paren-mode/format-text)
                        :text)]

      (clj->js (split-lines new-sexpr)))
    (catch :default e
      (js/debug "zip-it" e (.-stack e)))))

(defn run-transform [transformer nvim args cursor]
  (try
   (.getCurrentBuffer nvim
                      (fn [err buf]
                        (.getLineSlice buf 0 -1 true true
                                       (fn [err lines]
                                         (when-let [new-lines (zip-it transformer nvim lines cursor args)]
                                           (.setLineSlice buf 0 -1 true true new-lines))))))
   (catch :default e
     (js/debug "run-transform" e))))

(defn slurp [filename]
  (let [fs (js/require "fs")]
    (.readFileSync fs filename "utf8")))

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
                       (js/debug "connected")
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
