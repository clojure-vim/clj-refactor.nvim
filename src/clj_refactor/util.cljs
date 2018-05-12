(ns clj-refactor.util)

(defn echo-err [nvim v]
  (.callFunction nvim "Refactor_echoerr" (if (string? v)
                                           v
                                           (pr-str v))))


