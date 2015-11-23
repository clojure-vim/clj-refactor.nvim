(ns ^:figwheel-always nvim-refactor.main-test
    (:require [cljs.nodejs :as nodejs]
              [cljs.test :refer-macros [deftest is testing run-tests]]
              [nvim-parinfer.main :as m]))

(nodejs/enable-util-print!)
(println "Hello from the Node!")
(def -main (fn [] nil))
(set! *main-cli-fn* -main) ;; this is required

(deftest testing
  (is (= 1 1)))

(defn test-it []
  (run-tests))
