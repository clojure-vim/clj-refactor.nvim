(defproject nvim-refactor "0.1.0-SNAPSHOT"
  :description ""
  :url ""

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [org.clojure/core.async "0.2.374" :exclusions [org.clojure/tools.reader]]
                 [rewrite-cljs "0.3.1"]]

  :npm {:dependencies [[nrepl-client "0.2.3"]
                       [source-map-support "0.3.3"]
                       [parinfer "1.4.0"]]}

  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.5.0"]
            [lein-npm "0.6.1"]]

  :source-paths ["src" "target/classes"]

  :test-paths ["test"]

  :clean-targets ["rplugin/nvim-refactor" "target"]

  :figwheel {:server-port 9443}

  :cljsbuild {:builds [{:id "plugin"
                        :source-paths ["src"]
                        :compiler {:main nvim-refactor.main
                                   :asset-path "rplugin/node/nvim-refactor"
                                   :hashbang false
                                   :output-to "rplugin/node/nvim-refactor.js"
                                   :output-dir "rplugin/node/nvim-refactor"
                                   :optimizations :simple
                                   :target :nodejs
                                   :cache-analysis true
                                   :foreign-libs [{:file "node_modules/parinfer/parinfer.js"
                                                   :provides ["parinfer"]
                                                   :module-type :commonjs}]
                                   :closure-warnings {:const :off}
                                   :source-map "rplugin/node/nvim-refactor.js.map"}}
                       {:id "fig-test"
                        :source-paths ["src" "test"]
                        :figwheel {:on-jsload "nvim-refactor.main-test/test-it"}
                        :compiler {:main nvim-refactor.main-test
                                   :output-to "target/out/tests.js"
                                   :output-dir "target/out"
                                   :target :nodejs
                                   :foreign-libs [{:file "node_modules/parinfer/parinfer.js"
                                                   :provides ["parinfer"]
                                                   :module-type :commonjs}]
                                   :closure-warnings {:const :off}
                                   :optimizations :none
                                   :source-map true}}]})
