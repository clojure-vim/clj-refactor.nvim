(ns nvim-refactor.main
  (:require
   [cljs.nodejs :as nodejs]
   [cljs.reader :as reader]
   [clojure.zip :as zz]
   [nvim-refactor.edit :as edit]
   [nvim-refactor.transform :as transform]
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

(nodejs/enable-util-print!)

(defonce nconn (atom nil))

(defn jdbg [val & args]
  (if (exists? js/debug)
    (apply js/debug val args)
    (apply println val args))
  val)

(defn cdbg [val]
  (jdbg (pr-str val))
  val)

(defn split-lines [s]
  (string/split s #"\r?\n" -1))

(def fake-cursor [1 1 1 1])

(declare run-transform)

(defn nrepl-resolve-missing
  "Try to add a ns libspec based on whatever the middleware thinks."
  [conn nvim sym]
  (.send conn #js {:op "resolve-missing" :symbol (str sym) :debug "true"}
         (fn [err results]
           (try
            (let [cstr (aget (cdbg (first results)) "candidates")
                  candidates (reader/read-string cstr)]
              (cdbg candidates)
              (when (> (count candidates) 1)
                (jdbg "More than one candidate!"))
              ;; take first one for now - maybe can get input() choice
              (when-let [[missing missing-type] (first candidates)]
                (run-transform transform/add-candidate nvim [missing missing-type (namespace sym)] fake-cursor)))
            (catch :default e
              (jdbg "add-missing response exception" e e.stack))))))

(defn nrepl-namespace-aliases
  "Try to add a ns libspec based on already used aliases.
  Falls back to `resolve-missing`."
  [conn nvim sym]
  (.send conn #js {:op "namespace-aliases" :debug "true"}
         (fn [err results]
           (try
            (let [cstr (aget (first results) "namespace-aliases")
                  aliases (reader/read-string cstr)
                  sym-ns (namespace sym)]
              (if-let [missing (first (get-in aliases [:clj (symbol sym-ns)]))]
                (run-transform transform/add-candidate nvim [missing :ns sym-ns] fake-cursor)
                (nrepl-resolve-missing conn nvim sym)))

            (catch :default e
              (jdbg "add-missing namespace-aliases" e e.stack))))))

(defn add-missing-libspec
  "Asks repl for the missing libspec.
  When the repl comes back with response, run transform to add to ns"
  [nvim _ word]
  (when-let [conn @nconn]
    (let [sym (symbol word)]
      (jdbg "sending" (pr-str conn))
      (if (namespace sym)
        (nrepl-namespace-aliases conn nvim sym)
        (nrepl-resolve-missing conn nvim sym)))))

(defn clean-ns
  "Asks repl for the missing libspec.
  When the repl comes back with response, run transform to add to ns"
  [nvim _ path]
  (when-let [conn @nconn]
    (.send conn #js {:op "clean-ns" :path path :prefix-rewriting "false"}
           (fn [err results]
             (jdbg "clean-ns" err)
             (if-let [cstr (aget (first results) "ns")]
               (run-transform transform/replace-ns nvim [(parser/parse-string cstr)] fake-cursor)
               (.command nvim "echo No results for refactor"))))))

(defn zip-it
  "Finds the loc at row col of the file and runs the transformer-fn."
  [transformer lines row col args]
  (try
    (let [sexpr (string/join "\n" lines)
          pos {:row row :col col :end-row row :end-col col}
          new-sexpr (-> sexpr
                        (z/of-string)
                        (z/find-last-by-pos pos #(not= (z/tag %) :whitespace))
                        (transformer args)
                        ;; TODO should check if anything has changed
                        ;; - should return nil if transformer returned nil
                        (z/root-string)
                        (parinfer/parenMode)
                        (js->clj)
                        (get "text"))]
      (split-lines new-sexpr))
    (catch :default e
      (jdbg "zip-it" e (.-stack e))
      (throw e))))

(defn run-transform [transformer nvim args [_ row col _] & static-args]
  "Reads the current buffer, runs the transformation and modifies the current buffer with the result."
  (try
   (.getCurrentBuffer nvim
                      (fn [err buf]
                        (.getLineSlice buf 0 -1 true true
                                       (fn [err lines]
                                         (when-let [new-lines (clj->js (zip-it transformer (js->clj lines) row col (concat args static-args)))]
                                           (.setLineSlice buf 0 -1 true true new-lines))))))
   (catch :default e
     (jdbg "run-transform" e))))

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

(defn connect-to-repl [_ parent-directory]
  (try
   (let [dirs (reductions conj [] (string/split parent-directory #"/" -1))
         directories (reverse (remove #{""} (map (partial string/join "/") dirs)))
         port-files (map #(str % "/.nrepl-port") directories)
         valid-files (filter file-exists? port-files)]
     (if-let [port-file (first valid-files)]
       (let [port (js/parseInt (slurp port-file))
             client (js/require "nrepl-client")
             connection (.connect client #js {:port port})]
         (when-not @nconn
           (.on connection "error"
                (fn [err]
                  (jdbg "disconnected" err)
                  (reset! nconn nil)
                  (connect-to-repl nil parent-directory)))
           (.once connection "connect"
                  (fn []
                    (jdbg "connected" port (pr-str connection))
                    (reset! nconn connection)))))
       (jdbg "Can't find .nrepl-port in" (first directories))))
   (catch :default e
     (jdbg "EXCEPTION" e)
     (throw e))))

(defn -main []
  (try
   (when (exists? js/plugin)
     (jdbg "hello refactor")
     (.command js/plugin "CIntroduceLet" #js {:eval "getpos('.')" :nargs 1}
               (partial run-transform transform/introduce-let))
     (.command js/plugin "CExpandLet" #js {:eval "getpos('.')" :nargs "*"}
               (partial run-transform transform/expand-let))
     (.command js/plugin "CMoveToLet" #js {:eval "getpos('.')" :nargs 1}
               (partial run-transform transform/move-to-let))
     (.command js/plugin "CAddDeclaration" #js {:eval "getpos('.')" :nargs 0}
               (partial run-transform transform/add-declaration))
     (.command js/plugin "CCycleColl" #js {:eval "getpos('.')" :nargs 0}
               (partial run-transform transform/cycle-coll))
     (.command js/plugin "CCycleIf" #js {:eval "getpos('.')" :nargs 0}
               (partial run-transform transform/cycle-if))

     ;; REPL only commands
     (.autocmd js/plugin "BufEnter" #js {:pattern "*.clj" :eval "expand('%:p:h')"} connect-to-repl)
     (.command js/plugin "CAddMissingLibSpec" #js {:eval "expand('<cword>')" :nargs 0} add-missing-libspec)
     (.command js/plugin "CCleanNS" #js {:eval "expand('%:p')" :nargs 0} clean-ns))
   (catch js/Error e
     (jdbg "main exception" e))))

(set! *main-cli-fn* -main)
