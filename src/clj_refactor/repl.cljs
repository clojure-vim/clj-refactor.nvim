(ns clj-refactor.repl
  (:require
   [cljs.reader :as reader]
   [clojure.string :as string]
   [rewrite-clj.parser :as parser]
   [clj-refactor.transform :as transform]
   [cljs.core.async :refer [close! chan <!]])
  (:require-macros
   [cljs.core.async.macros :refer [go]]))

(defn handle-fireplace
  [done-ch nvim args cb results]
  (js/console.debug "First debug of handle-fireplace" (pr-str args) (pr-str results))
  (cond
    (not results)
    (throw (ex-info "Unable to get results"
                    {:message (str (pr-str args) "=>" (pr-str results))}))

    (aget (first results) "error")
    (throw (ex-info "Error during fireplace#message: "
                    {:message (aget (first results) "error")}))

    :else
    (do
     (js/console.debug (:op args) results)
     (cb (first results)))))

(defn fireplace-message
  ([nvim args cb]
   (fireplace-message true (chan) nvim args cb))
  ([done-ch nvim args cb]
   (fireplace-message true done-ch nvim args cb))
  ([save? done-ch nvim args cb]
   (-> (if save?
         (.command nvim "w")
         (js/Promise.resolve 0))
       (.then (fn [_]
                (.callFunction nvim "fireplace#message" (clj->js [(clj->js args)]))))
       (.catch (fn [err]
                (js/console.debug (pr-str args) err)
                (.command nvim (str "echoerr \"Error: " err "\""))
                (close! done-ch)))
       (.then (partial handle-fireplace done-ch nvim args cb))
       (.catch (fn [err]
                 (.command nvim (str "echo \"Error: " (str (.-message err) (-> err ex-data :message)) "\""))
                 (close! done-ch))))))

(defn nrepl-resolve-missing
  "Try to add a ns libspec based on whatever the middleware thinks."
  [done-ch run-transform nvim sym cursor]
  (fireplace-message
   false
   done-ch
   nvim
   {:op "resolve-missing" :symbol (str sym)}
   (fn [result]
     (let [cstr (aget result "candidates")]
        (if (seq cstr)
          (let [candidates (reader/read-string cstr)]
            (when (> (count candidates) 1)
              (js/console.debug "More than one candidate!" candidates))
            ;; take first one for now - maybe can get input() choice
            (if-let [{:keys [name type]} (first candidates)]
              (run-transform done-ch transform/add-candidate nvim [name type (namespace sym)] cursor)
              (close! done-ch))))
        (do
          (.command nvim "echo 'No candidates'")
          (close! done-ch))))))

(defn nrepl-namespace-aliases
  "Try to add a ns libspec based on already used aliases.
  Falls back to `resolve-missing`."
  [done-ch run-transform nvim sym cursor]
  (fireplace-message
   false
   done-ch
   nvim
   {:op "namespace-aliases"}
   (fn [result]
     (let [cstr (aget result "namespace-aliases")
            aliases (reader/read-string cstr)
            sym-ns (namespace sym)]
        (if-let [missing (first (get-in aliases [:clj (symbol sym-ns)]))]
          (run-transform done-ch transform/add-candidate nvim [missing :ns sym-ns] cursor)
          (nrepl-resolve-missing done-ch run-transform nvim sym cursor))))))

(defn add-missing-libspec
  "Asks repl for the missing libspec.
  When the repl comes back with response, run transform to add to ns"
  [done-ch run-transform nvim _ [cursor word]]
  (let [sym (symbol word)]
      (if (namespace sym)
        (nrepl-namespace-aliases done-ch run-transform nvim sym cursor)
        (nrepl-resolve-missing done-ch run-transform nvim sym cursor))))

(defn clean-ns
  "Asks repl for the missing libspec.
  When the repl comes back with response, run transform to add to ns"
  [done-ch run-transform nvim _ [cursor path prune-ns-form prefix-rewriting]]
  (fireplace-message
   done-ch
   nvim
   {:op "clean-ns"
    :path path
    :prefix-rewriting (if (pos? prefix-rewriting) "true" "false")
    :prune-ns-form (if (pos? prune-ns-form) "true" "false")}
   (fn [result]
     (let [ns-str (aget result "ns")]
       (if (string? ns-str)
         (run-transform
          done-ch transform/replace-ns nvim [(parser/parse-string ns-str)] cursor)
         (close! done-ch))))))

(defn rename-file
  [nvim [new-file] current-file]
  (fireplace-message
   nvim
   {:op "rename-file-or-dir" :old-path current-file :new-path new-file}
   (fn [result]
     (let [touched (aget result "touched")]
       (.command nvim (str "e " new-file " | silent! bp | bd"))))))

(defn rename-dir
  [nvim [new-dir] current-dir]
  (fireplace-message
   nvim
   {:op "rename-file-or-dir" :old-path current-dir :new-path new-dir}
   (fn [result]
     (let [touched (aget result "touched")]
       (.command nvim (str "e " new-dir " | silent! bp | bd"))))))


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
     (fn [result]
       (let [edn (aget result "definition")
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
   (fn [result]
     (let [used-locals (seq (js->clj (aget result "used-locals")))]
        (run-transform transform-fn nvim (conj (js->clj args) used-locals) [0 row col 0])))))

(defn magic-requires
  [done-ch run-transform nvim args [cursor path word]]
  (go
   (let [cram-ch (chan)
         clean-ch (chan)]
     (add-missing-libspec cram-ch run-transform nvim args [cursor word])
     (js/console.debug "waiting on cram")
     (<! cram-ch)
     (clean-ns clean-ch run-transform nvim args [cursor path])
     (js/console.debug "waiting on clean")
     (<! clean-ch)
     (js/console.debug "closing magic")
     (close! done-ch))))

