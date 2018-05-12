(ns clj-refactor.main
  (:require
   [cljs.nodejs :as nodejs]
   [clojure.zip :as zz]
   [clj-refactor.edit :as edit]
   [clj-refactor.repl :as repl]
   [clj-refactor.transform :as transform]
   [cljfmt.core :as cljfmt]
   [clojure.string :as string]
   [goog.object :as object]
   [rewrite-clj.node :as n]
   [rewrite-clj.node.forms :as nf]
   [rewrite-clj.parser :as parser]
   [rewrite-clj.paredit :as p]
   [rewrite-clj.zip :as z]
   [rewrite-clj.zip.base :as zb]
   [rewrite-clj.zip.findz :as zf]
   [rewrite-clj.zip.removez :as zr]
   [rewrite-clj.zip.utils :as zu]
   [rewrite-clj.zip.whitespace :as ws]
   [cljs.core.async :refer [close! chan <!]])
  (:require-macros
   [cljs.core.async.macros :refer [go]]))

(nodejs/enable-util-print!)

(defn every [& args]
  (js/Promise.all (into-array args)))

(defn jdbg [val & args]
  (apply js/console.debug val args)
  val)

(defn cdbg [val]
  (jdbg (pr-str val))
  val)

(defn split-lines [s]
  (string/split s #"\r?\n" -1))

(defn swap-position!
  [zloc cursor-ref offset]
  (swap! cursor-ref edit/read-position zloc offset)
  zloc)

(defn zip-it
  "Finds the loc at row col of the file and runs the transformer-fn."
  [transformer lines row col args]
  (try
    (let [new-cursor (atom [row col])
          sexpr (string/join "\n" lines)
          pos {:row row :col col :end-row row :end-col col}
          zloc (-> sexpr
                   (z/of-string)
                   (z/find-last-by-pos pos #(not= (z/tag %) :whitespace)))
          zpos (meta (z/node zloc))
          offset (- col (:col zpos))
          new-sexpr (-> zloc
                        (edit/mark-position :new-cursor)
                       ;; TODO should check if anything has changed
                       ;; - should return nil if transformer returned nil
                        (transformer args)
                        (edit/mark-position :reformat)
                        (edit/format-marked)
                        (edit/find-mark :new-cursor)
                        (swap-position! new-cursor offset)
                        (z/root-string))]
      (let [[row col] @new-cursor]
        {:row row
         :col col
         :new-lines (split-lines new-sexpr)}))
    (catch :default e
      (jdbg "zip-it" e (.-stack e))
      (throw e))))

(defn run-transform* [done-ch transformer nvim args [_ cur-row cur-col _] & static-args]
  "Reads the current buffer, runs the transformation and modifies the current buffer with the result."
  (try
    (-> (.-buffer nvim)
        (.then
         (fn [buf]
           (every buf (.getLines buf #js {:start 0
                                          :end -1}))))
        (.then
         (fn [[buf lines]]
           (if-let [{:keys [row col new-lines]} (zip-it transformer (js->clj lines) cur-row cur-col (concat args static-args))]
             (-> (.setLines buf (clj->js new-lines) #js {:start 0 :end -1})
                 (.catch
                  (fn [err]
                    (.command nvim (str "call cursor(" row "," col ")")))))
             nil)))
        (.then
         (fn [_]
           (close! done-ch)))
        (.catch (fn [e]
                  (.command nvim (str "echoerr \"" (.-message e) "\""))
                  (close! done-ch))))
    (catch :default e
      (jdbg "run-transform" e))))

(defn channel-promise
  [c]
  (new js/Promise
       (fn [resolve reject]
         (go (resolve (<! c))))))

(defn run-transform
  [transformer nvim args cursor & static-args]
  (let [done-ch (chan)]
    (go
      (apply run-transform* done-ch transformer nvim args cursor static-args))
    (channel-promise done-ch)))

(defn run-repl
  [fn-thing nvim args eval-result static-args]
  (let [done-ch (chan)]
    (go
      (let [done-ch (chan)]
        (fn-thing done-ch run-transform* nvim args eval-result)))
    (channel-promise done-ch)))

(defn legacy-opts-wrap-fn
  [plugin f opts]
  (fn [args]
    (let [nvim (.-nvim plugin)]
      (-> (if (:eval opts)
            (.eval nvim (:eval opts))
            (every []))
          (.then
           (fn [eval-result]
             (f nvim (js->clj args) eval-result)))))))

(defn register-command
  ([plugin name f]
   (.registerCommand plugin name f))
  ([plugin name f opts]
   (.registerCommand plugin
                     name
                     (legacy-opts-wrap-fn plugin f opts)
                     (clj->js (dissoc opts :eval)))))

(defn ^:export -main [plugin]
  (doto plugin
    (register-command "CAddDeclaration"
                      (partial run-transform transform/add-declaration)
                      {:nargs 0, :eval "getpos('.')"})
    (register-command "CCycleColl"
                      (partial run-transform transform/cycle-coll)
                      {:eval "getpos('.')" :nargs 0})
    (register-command "CCycleIf"
                      (partial run-transform transform/cycle-if)
                      {:eval "getpos('.')" :nargs 0})
    (register-command "CCyclePrivacy"
                      (partial run-transform transform/cycle-privacy)
                      {:eval "getpos('.')" :nargs 0})
    (register-command "CCycleThread"
                      (partial run-transform transform/cycle-thread)
                      {:eval "getpos('.')" :nargs 0})
    (register-command "CExpandLet"
                      (partial run-transform transform/expand-let)
                      {:eval "getpos('.')" :nargs 0})
    (register-command "CExtractDef"
                      (partial run-transform transform/extract-def)
                      {:eval "getpos('.')" :nargs 1})
    (register-command "CFunctionFromExample"
                      (partial run-transform transform/function-from-example)
                      {:eval "getpos('.')" :nargs 0})
    (register-command "CIntroduceLet"
                      (partial run-transform transform/introduce-let)
                      {:eval "getpos('.')" :nargs 1})
    (register-command "CMoveToLet"
                      (partial run-transform transform/move-to-let)
                      {:eval "getpos('.')" :nargs 1})
    (register-command "CThread"
                      (partial run-transform transform/thread)
                      {:eval "getpos('.')" :nargs 0})
    (register-command "CThreadFirstAll"
                      (partial run-transform transform/thread-first-all)
                      {:eval "getpos('.')" :nargs 0})
    (register-command "CThreadLast"
                      (partial run-transform transform/thread-last)
                      {:eval "getpos('.')" :nargs 0})
    (register-command "CThreadLastAll"
                      (partial run-transform transform/thread-last-all)
                      {:eval "getpos('.')" :nargs 0})
    (register-command "CUnwindAll"
                      (partial run-transform transform/unwind-all)
                      {:eval "getpos('.')" :nargs 0})
    (register-command "CUnwindThread"
                      (partial run-transform transform/unwind-thread)
                      {:eval "getpos('.')" :nargs 0})
    (register-command "CFormatAll"
                      (partial run-transform transform/format-all)
                      {:eval "getpos('.')" :nargs 0})
    (register-command "CFormatForm"
                      (partial run-transform transform/format-form)
                      {:eval "getpos('.')" :nargs 0})

    ;; REPL only commands:
    (register-command "CAddMissingLibSpec"
                      (partial run-repl repl/add-missing-libspec)
                      {:eval "[getpos('.'), expand('<cword>')]" :nargs 0})
    (register-command "CCleanNS"
                      (partial run-repl repl/clean-ns)
                      {:eval (str "[getpos('.'), expand('%:p'),"
                                  " (exists('g:clj_refactor_prune_ns_form') ? g:clj_refactor_prune_ns_form : 1),"
                                  " (exists('g:clj_refactor_prefix_rewriting') ? g:clj_refactor_prefix_rewriting : 1)]")
                       :nargs 0})
    (register-command "CRenameFile"
                      repl/rename-file
                      {:eval "expand('%:p')" :nargs 1 :complete "file"})
    (register-command "CRenameDir"
                      repl/rename-dir
                      {:eval "expand('%:p:h')" :nargs 1 :complete "dir"})
    (register-command "CRenameSymbol"
                      (partial repl/extract-definition repl/rename-symbol)
                      {:eval "[getcwd(), expand('%:p'), fireplace#ns(), expand('<cword>'), fireplace#info(expand('<cword>')), getpos('.')]" :nargs 1})
    (register-command "CExtractFunction"
                      (partial repl/find-used-locals run-transform transform/extract-function)
                      {:eval "[expand('%:p'), getpos('.')]" :nargs 1})
    (register-command "CMagicRequires"
                      (partial run-repl repl/magic-requires)
                      {:eval "[getpos('.'), expand('%:p'), expand('<cword>')]" :nargs 0})))
