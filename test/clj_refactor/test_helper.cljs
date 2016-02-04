(ns clj-refactor.test-helper
  (:require
   [cljs.reader :as r]
   [rewrite-clj.zip :as z]))

(defn zip-to
  [form goto f]
  (-> form
      (str)
      (z/of-string)
      (z/find z/next #(= (z/sexpr %) goto))
      (f)))

(defn apply-goto
  [form goto f]
  (zip-to form goto f))

(defn apply-zip-to
  [form goto f]
  (z/sexpr (apply-goto form goto f)))

(defn apply-zip
  [form goto f]
  (-> (apply-goto form goto f)
      (z/root-string)
      (r/read-string)))

(defn apply-zip-root
  [form goto f]
  (r/read-string
   (str "(do "
        (z/root-string (apply-goto form goto f))
        ")")))
