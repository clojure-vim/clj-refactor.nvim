(ns nvim-refactor.edit
 (:require
   [rewrite-clj.zip :as z]
   [rewrite-clj.zip.utils :as zu]
   [rewrite-clj.zip.whitespace :as ws]))

(defn remove-right [zloc]
  (cond
   (and (z/rightmost? zloc)
        (z/leftmost? zloc))
   (-> zloc
     (z/remove))

   (z/rightmost? zloc)
   (z/remove zloc)

   :else
   (-> zloc
       (z/right)
       (z/remove))))

(defn remove-left [zloc]
  (-> zloc
    (zu/remove-left-while ws/whitespace?)
    (zu/remove-left-while (complement ws/whitespace?))))

