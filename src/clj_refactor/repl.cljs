(ns clj-refactor.repl
  (:require
   [cljs.reader :as reader]
   [clojure.string :as string]
   [rewrite-clj.parser :as parser]
   [clj-refactor.transform :as transform]
   [cljs.core.async :refer [close! chan <!]])
  (:require-macros
   [cljs.core.async.macros :refer [go]]))


(defn fireplace-message
  [nvim args cb]
  ;; Most refactor commands work on saved files
  (.command nvim "w"
            (fn [err]
              (.callFunction nvim "fireplace#message" (clj->js [(clj->js args)]) cb))))

(defn nrepl-resolve-missing
  "Try to add a ns libspec based on whatever the middleware thinks."
  [run-transform nvim sym cursor]
  (fireplace-message
   nvim
   {:op "resolve-missing" :symbol (str sym) :debug "true"}
   (fn [err results]
     (try
      (let [cstr (aget (first results) "candidates")]
        (if (seq cstr)
          (let [candidates (reader/read-string cstr)]
            (when (> (count candidates) 1)
              (js/debug "More than one candidate!" candidates))
            ;; take first one for now - maybe can get input() choice
            (when-let [{:keys [name type]} (first candidates)]
              (run-transform transform/add-candidate nvim [name type (namespace sym)] cursor))))
        (.command nvim "echo 'No candidates'"))
      (catch :default e
        (js/debug "add-missing response exception" e e.stack))))))

(defn nrepl-namespace-aliases
  "Try to add a ns libspec based on already used aliases.
  Falls back to `resolve-missing`."
  [run-transform nvim sym cursor]
  (fireplace-message
   nvim
   {:op "namespace-aliases" :debug "true"}
   (fn [err results]
     (try
      (let [cstr (aget (first results) "namespace-aliases")
            aliases (reader/read-string cstr)
            sym-ns (namespace sym)]
        (if-let [missing (first (get-in aliases [:clj (symbol sym-ns)]))]
          (run-transform transform/add-candidate nvim [missing :ns sym-ns] cursor)
          (nrepl-resolve-missing run-transform nvim sym cursor)))
      (catch :default e
        (js/debug "add-missing namespace-aliases" e e.stack))))))

(defn add-missing-libspec
  "Asks repl for the missing libspec.
  When the repl comes back with response, run transform to add to ns"
  [run-transform nvim _ [cursor word]]
  (let [sym (symbol word)]
      (if (namespace sym)
        (nrepl-namespace-aliases run-transform nvim sym cursor)
        (nrepl-resolve-missing run-transform nvim sym cursor))))

(defn clean-ns
  "Asks repl for the missing libspec.
  When the repl comes back with response, run transform to add to ns"
  [run-transform nvim _ [cursor path]]
  (fireplace-message
   nvim
   {:op "clean-ns" :path path :prefix-rewriting "false"}
   (fn [err results]
     (js/debug "clean-ns" err results)
     (let [ns-str (aget (first results) "ns")]
       (when (string? ns-str)
         (run-transform
          transform/replace-ns nvim [(parser/parse-string ns-str)] cursor))))))

(defn rename-file
  [nvim [new-file] current-file]
  (fireplace-message
   nvim
   {:op "rename-file-or-dir" :old-path current-file :new-path new-file}
   (fn [err results]
     (js/debug "rename-file" err results)
     (if-let [touched (aget (first results) "touched")]
       (.command nvim (str "e " new-file " | silent! bp | bd"))
       (.command nvim (str "echo 'Nothing touched: " (str results) "'"))))))

(defn rename-dir
  [nvim [new-dir] current-dir]
  (fireplace-message
   nvim
   {:op "rename-file-or-dir" :old-path current-dir :new-path new-dir}
   (fn [err results]
     (js/debug "rename-dir" err results)
     (if-let [touched (aget (first results) "touched")]
       (.command nvim (str "e " new-dir " | silent! bp | bd"))
       (.command nvim (str "echo \"Nothing touched: "
                           (aget (first results) "error") "\""))))))

(defn extract-definition
  [transform-fn nvim args [project-dir buffer-file buffer-ns word info [_ row col _]]]
  (let [ns (or (aget info "ns") buffer-ns)
        name (or (aget info "name") word)]
    (fireplace-message
     nvim
     {:op "extract-definition"
      :file buffer-file
      :dir project-dir
      :ns ns
      :name name
      :line row
      :column col}
     (fn [err results]
       (js/debug "extract-definition" err results)
       (let [edn (aget (first results) "definition")
             defs (reader/read-string edn)]
         (apply transform-fn nvim ns name defs args))))))

(defn rename-symbol
  [nvim sym-ns sym-name defs new-symbol]
  (go
    ;; TODO look at clj-refactor.el for safer impl
    (let [wait-ch (chan)]
      (let [{:keys [line-beg col-beg file name]} (:definition defs)]
        (.command nvim (str "e " file " | "
                            "call cursor(" line-beg "," col-beg ") | "
                            "exe \"normal! w/" sym-name "\ncw" new-symbol "\" | "
                            "w")))
      (doseq [occurrence (conj (:occurrences defs))
              :let [{:keys [line-beg col-beg file name]} occurrence]]
        (.command nvim (str "e " file " | "
                            "call cursor(" line-beg "," col-beg ") | "
                            "exe \"normal! cw" new-symbol "\" | "
                            "w")
                  (fn [err]
                    (close! wait-ch)))
        (<! wait-ch)))))

(defn find-used-locals
  [run-transform transform-fn nvim args [file [_ row col _]]]
  (fireplace-message
   nvim
   {:op "find-used-locals"
    :file file
    :line row
    :column col}
   (fn [err results]
     (js/debug "find-used-locals" err results)
     (if-let [error (aget (first results) "error")]
       (.command nvim (str "echo \"" error "\""))
       (let [used-locals (seq (js->clj (aget (first results) "used-locals")))]
        (run-transform transform-fn nvim (conj (js->clj args) used-locals) [0 row col 0]))))))
