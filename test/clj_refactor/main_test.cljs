(ns ^:figwheel-always clj-refactor.main-test
    (:require [cljs.nodejs :as nodejs]
              [cljs.test :refer-macros [run-tests]]
              [clj-refactor.edit-test :as et]
              [clj-refactor.transform-test :as tt]))

(nodejs/enable-util-print!)
(def -main (fn [] nil))
(set! *main-cli-fn* -main)

(defn test-it []
  (run-tests 'clj-refactor.edit-test
             'clj-refactor.transform-test))
