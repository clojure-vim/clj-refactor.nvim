(defproject clj-refactor "0.1.1"
  :description ""
  :url ""

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.655"]
                 [org.clojure/core.async "0.2.374" :exclusions [org.clojure/tools.reader]]
                 [rewrite-cljs "0.4.3" :exclusions [org.clojure/tools.reader]]
                 [cljfmt "0.5.6"]]

  :npm {:dependencies [[source-map-support "0.3.3"]
                       [ws "1.0.1"]]}

  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.5.16"]
            [lein-npm "0.6.1"]]

  :source-paths ["src" "target/classes"]

  :test-paths ["test"]

  :clean-targets ["rplugin/node/clj-refactor" "target"]

  :figwheel {:server-port 9443}

  :cljsbuild {:builds [{:id "plugin"
                        :source-paths ["src"]
                        :compiler {:main clj-refactor.main
                                   :asset-path "rplugin/node/clj-refactor/build/"
                                   :hashbang false
                                   :output-to "rplugin/node/clj-refactor/compiled.js"
                                   :output-dir "rplugin/node/clj-refactor/build/"
                                   :language-in :ecmascript5
                                   :optimizations :simple
                                   :target :nodejs
                                   :pretty-print true
                                   :cache-analysis true
                                   :source-map "rplugin/node/clj-refactor/compiled.js.map"}}
                       {:id "fig-test"
                        :source-paths ["src" "test"]
                        :figwheel {:on-jsload "clj-refactor.main-test/test-it"}
                        :compiler {:main clj-refactor.main-test
                                   :output-to "target/out/tests.js"
                                   :output-dir "target/out"
                                   :target :nodejs
                                   :optimizations :none
                                   :source-map true}}]})
