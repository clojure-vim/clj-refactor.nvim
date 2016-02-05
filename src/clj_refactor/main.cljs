(ns clj-refactor.main
  (:require
   [cljs.nodejs :as nodejs]
   [clojure.zip :as zz]
   [clj-refactor.edit :as edit]
   [clj-refactor.repl :as repl]
   [clj-refactor.transform :as transform]
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

(declare run-transform)

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
                       (edit/find-mark :new-cursor)
                       (swap-position! new-cursor offset)
                       (z/root-string)
                       (parinfer/parenMode)
                       (aget "text"))]
     (let [[row col] @new-cursor]
       {:row row
        :col col
        :new-lines (split-lines new-sexpr)}))
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
                                         (when-let [{:keys [row col new-lines]} (zip-it transformer (js->clj lines) row col (concat args static-args))]
                                           (jdbg "saving" row col)
                                           (try
                                             (.setLineSlice buf 0 -1 true true (clj->js new-lines)
                                                            (fn [err]
                                                             (.command nvim (str "call cursor("row "," col")"))))
                                            (catch :default e
                                              (jdbg "save" e (.-stack e)))))))))
   (catch :default e
     (jdbg "run-transform" e))))

(defn -main []
  (try
   (when (exists? js/plugin)
     (jdbg "hello refactor")
     (.command js/plugin "CAddDeclaration" #js {:eval "getpos('.')" :nargs 0} (partial run-transform transform/add-declaration))
     (.command js/plugin "CCycleColl" #js {:eval "getpos('.')" :nargs 0} (partial run-transform transform/cycle-coll))
     (.command js/plugin "CCycleIf" #js {:eval "getpos('.')" :nargs 0} (partial run-transform transform/cycle-if))
     (.command js/plugin "CCyclePrivacy" #js {:eval "getpos('.')" :nargs 0} (partial run-transform transform/cycle-privacy))
     (.command js/plugin "CCycleThread" #js {:eval "getpos('.')" :nargs 0} (partial run-transform transform/cycle-thread))
     (.command js/plugin "CExpandLet" #js {:eval "getpos('.')" :nargs 0} (partial run-transform transform/expand-let))
     (.command js/plugin "CFunctionFromExample" #js {:eval "getpos('.')" :nargs 0} (partial run-transform transform/function-from-example))
     (.command js/plugin "CIntroduceLet" #js {:eval "getpos('.')" :nargs 1} (partial run-transform transform/introduce-let))
     (.command js/plugin "CMoveToLet" #js {:eval "getpos('.')" :nargs 1} (partial run-transform transform/move-to-let))
     (.command js/plugin "CThread" #js {:eval "getpos('.')" :nargs 0} (partial run-transform transform/thread))
     (.command js/plugin "CThreadFirstAll" #js {:eval "getpos('.')" :nargs 0} (partial run-transform transform/thread-first-all))
     (.command js/plugin "CThreadLast" #js {:eval "getpos('.')" :nargs 0} (partial run-transform transform/thread-last))
     (.command js/plugin "CThreadLastAll" #js {:eval "getpos('.')" :nargs 0} (partial run-transform transform/thread-last-all))
     (.command js/plugin "CUnwindAll" #js {:eval "getpos('.')" :nargs 0} (partial run-transform transform/unwind-all))
     (.command js/plugin "CUnwindThread" #js {:eval "getpos('.')" :nargs 0} (partial run-transform transform/unwind-thread))

     ;; REPL only commands
     (.command js/plugin "CAddMissingLibSpec" #js {:eval "[getpos('.'), expand('<cword>')]" :nargs 0} (partial repl/add-missing-libspec run-transform))
     (.command js/plugin "CCleanNS" #js {:eval "[getpos('.'), expand('%:p')]" :nargs 0} (partial repl/clean-ns run-transform))
     (.command js/plugin "CRenameFile" #js {:eval "expand('%:p')" :nargs 1 :complete "file"} repl/rename-file)
     (.command js/plugin "CRenameDir" #js {:eval "expand('%:p:h')" :nargs 1 :complete "dir"} repl/rename-dir)
     (.command js/plugin "CRenameSymbol"
               #js {:eval "[getcwd(), expand('%:p'), fireplace#ns(), expand('<cword>'), fireplace#info(expand('<cword>')), getpos('.')]" :nargs 1}
               (partial repl/extract-definition repl/rename-symbol))
     (.command js/plugin "CExtractFunction"
               #js {:eval "[expand('%:p'), getpos('.')]" :nargs 1}
               (partial repl/find-used-locals run-transform transform/extract-function)))

   (catch js/Error e
     (jdbg "main exception" e))))

(set! *main-cli-fn* -main)
