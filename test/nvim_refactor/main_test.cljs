(ns ^:figwheel-always nvim-refactor.main-test
    (:require [cljs.nodejs :as nodejs]
              [cljs.test :refer-macros [deftest is testing run-tests]]
              [clojure.string :as str]
              [nvim-refactor.main :as m]
              [nvim-refactor.edit :as e]
              [nvim-refactor.transform :as t]))

(nodejs/enable-util-print!)
(def -main (fn [] nil))
(set! *main-cli-fn* -main)

(defn test-it []
  (run-tests 'nvim-refactor.edit-test
             'nvim-refactor.transform-test
             'nvim-refactor.main-test))
