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

(defn apply-zip-to
  [form goto f]
  (-> form
      (zip-to goto f)
      (z/sexpr)))

(defn apply-zip
  [form goto f]
  (-> form
      (zip-to goto f)
      (z/root-string)
      (r/read-string)))

