(defproject clj-refactor "0.1.1"
  :description ""
  :url ""

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [org.clojure/core.async "0.2.374" :exclusions [org.clojure/tools.reader]]
                 [rewrite-cljs "0.4.0" :exclusions [org.clojure/tools.reader]]
                 [cljfmt "0.4.1"]]

  :npm {:dependencies [[source-map-support "0.3.3"]
                       [ws "1.0.1"]]}

  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.5.0-6"]
            [lein-npm "0.6.1"]]

  :source-paths ["src" "target/classes"]

  :test-paths ["test"]

  :clean-targets ["rplugin/clj-refactor" "target"]

  :figwheel {:server-port 9443}

  :cljsbuild {:builds [{:id "plugin"
                        :source-paths ["src"]
                        :compiler {:main clj-refactor.main
                                   :asset-path "rplugin/node/clj-refactor"
                                   :hashbang false
                                   :output-to "rplugin/node/clj-refactor.js"
                                   :output-dir "rplugin/node/clj-refactor"
                                   :optimizations :simple
                                   :target :nodejs
                                   :cache-analysis true
                                   :source-map "rplugin/node/clj-refactor.js.map"}}
                       {:id "fig-test"
                        :source-paths ["src" "test"]
                        :figwheel {:on-jsload "clj-refactor.main-test/test-it"}
                        :compiler {:main clj-refactor.main-test
                                   :output-to "target/out/tests.js"
                                   :output-dir "target/out"
                                   :target :nodejs
                                   :optimizations :none
                                   :source-map true}}]})
