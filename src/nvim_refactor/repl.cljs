(ns nvim-refactor.repl
  (:require
   [cljs.reader :as reader]
   [clojure.string :as string]
   [rewrite-clj.parser :as parser]
   [nvim-refactor.transform :as transform]
   [cljs.core.async :refer [close! chan <!]])
  (:require-macros
   [cljs.core.async.macros :refer [go]]))

(def fake-cursor [1 1 1 1])

(defonce nconn (atom nil))

(defn nrepl-resolve-missing
  "Try to add a ns libspec based on whatever the middleware thinks."
  [run-transform conn nvim sym]
  (.send conn #js {:op "resolve-missing" :symbol (str sym) :debug "true"}
         (fn [err results]
           (try
            (let [cstr (aget (first results) "candidates")]
              (if (seq cstr)
                (let [candidates (reader/read-string cstr)]
                  (when (> (count candidates) 1)
                    (js/debug "More than one candidate!" candidates))
                  ;; take first one for now - maybe can get input() choice
                  (when-let [{:keys [name type]} (first candidates)]
                    (run-transform transform/add-candidate nvim [name type (namespace sym)] fake-cursor))))
              (.command nvim "echo 'No candidates'"))
            (catch :default e
              (js/debug "add-missing response exception" e e.stack))))))

(defn nrepl-namespace-aliases
  "Try to add a ns libspec based on already used aliases.
  Falls back to `resolve-missing`."
  [run-transform conn nvim sym]
  (.send conn #js {:op "namespace-aliases" :debug "true"}
         (fn [err results]
           (try
            (let [cstr (aget (first results) "namespace-aliases")
                  aliases (reader/read-string cstr)
                  sym-ns (namespace sym)]
              (if-let [missing (first (get-in aliases [:clj (symbol sym-ns)]))]
                (run-transform transform/add-candidate nvim [missing :ns sym-ns] fake-cursor)
                (nrepl-resolve-missing run-transform conn nvim sym)))
            (catch :default e
              (js/debug "add-missing namespace-aliases" e e.stack))))))

(defn add-missing-libspec
  "Asks repl for the missing libspec.
  When the repl comes back with response, run transform to add to ns"
  [run-transform nvim _ word]
  (if-let [conn @nconn]
    (let [sym (symbol word)]
      (if (namespace sym)
        (nrepl-namespace-aliases run-transform conn nvim sym)
        (nrepl-resolve-missing run-transform conn nvim sym)))
    (.command nvim "echo 'Cannot run, no repl connection'")))

(defn clean-ns
  "Asks repl for the missing libspec.
  When the repl comes back with response, run transform to add to ns"
  [run-transform nvim _ path]
  (if-let [conn @nconn]
    (.send conn #js {:op "clean-ns" :path path :prefix-rewriting "false"}
           (fn [err results]
             (js/debug "clean-ns" err)
             (when-let [cstr (aget (first results) "ns")]
               (run-transform
                transform/replace-ns nvim [(parser/parse-string cstr)] fake-cursor))))
    (.command nvim "echo 'Cannot run, no repl connection'")))

(defn rename-file
  [nvim [new-file] current-file]
  (if-let [conn @nconn]
      (.send conn #js {:op "rename-file-or-dir" :old-path current-file :new-path new-file}
             (fn [err results]
               (js/debug "rename-file" err results)
               (if-let [touched (aget (first results) "touched")]
                 (.command nvim (str "e " new-file " | silent! bp | bd"))
                 (.command nvim (str "echo 'Nothing touched: " (str results) "'")))))
      (.command nvim "echo 'Cannot run, no repl connection'")))

(defn rename-dir
  [nvim [new-dir] current-dir]
  (if-let [conn @nconn]
      (.send conn #js {:op "rename-file-or-dir" :old-path current-dir :new-path new-dir}
             (fn [err results]
               (js/debug "rename-dir" err results)
               (if-let [touched (aget (first results) "touched")]
                 (.command nvim (str "e " new-dir " | silent! bp | bd"))
                 (.command nvim (str "echo \"Nothing touched: "
                                     (aget (first results) "error") "\"")))))
      (.command nvim "echo 'Cannot run, no repl connection'")))

(defn extract-definition
  [transform-fn nvim args [project-dir info [_ row col _]]]
  (if-let [conn @nconn]
    (let [ns (aget info "ns")
          name (aget info "name")
          file (aget info "file")]
      (.send conn #js {:op "extract-definition"
                       :file (string/replace file #"^file:" "")
                       :dir project-dir
                       :ns ns
                       :name name
                       :line row
                       :column col}
             (fn [err results]
               (js/debug "extract-definition" err results)
               (let [edn (aget (first results) "definition")
                     defs (reader/read-string edn)]
                 (apply transform-fn nvim ns name defs args)))))))

(defn rename-symbol
  [nvim sym-ns sym-name defs new-symbol]
  (go
    (let [wait-ch (chan)]
      (doseq [occurrence (conj (:occurrences defs) (:definition defs))
              :let [{:keys [line-beg col-beg file name]} occurrence]]
        (.command nvim (str "e " file " | "
                            "call cursor(" line-beg "," col-beg ") | "
                            "exe \"normal /" sym-name "\ncw" new-symbol "\" | "
                            "s")
                  (fn [err]
                    (close! wait-ch)))
        (<! wait-ch)))))

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
                  (js/debug "disconnected" err)
                  (reset! nconn nil)
                  (connect-to-repl nil parent-directory)))
           (.once connection "connect"
                  (fn []
                    (js/debug "connected" port (pr-str connection))
                    (reset! nconn connection)))))
       (js/debug "Can't find .nrepl-port in" (first directories))))
   (catch :default e
     (js/debug "EXCEPTION" e)
     (throw e))))
