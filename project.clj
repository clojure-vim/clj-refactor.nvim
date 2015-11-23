(defproject nvim-refactor "0.1.0-SNAPSHOT"
  :description ""
  :url ""

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [org.clojure/core.async "0.2.374" :exclusions [org.clojure/tools.reader]]
                 [rewrite-cljs "0.3.1"]
                 [parinfer "0.1.0"]]

  :npm {:dependencies [[nrepl-client "0.2.3"]]}

  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.5.0"]
            [lein-npm "0.6.1"]]

  :source-paths ["src" "target/classes"]

  :test-paths ["test"]

  :clean-targets ["rplugin/nvim-refactor" "rplugin/nvim-refactor" "target"]

  :figwheel {:server-port 9443}

  :cljsbuild {:builds [{:id "plugin"
                        :source-paths ["src"]
                        :compiler {:main nvim-refactor.main
                                   :asset-path "rplugin/nvim-refactor"
                                   :hashbang false
                                   :output-to "rplugin/nvim-refactor.js"
                                   :output-dir "rplugin/nvim-refactor"
                                   :optimizations :simple
                                   :target :nodejs
                                   :cache-analysis true
                                   :source-map "rplugin/nvim-refactor.js.map"}}
                       {:id "fig-test"
                        :source-paths ["src" "test"]
                        :figwheel {:on-jsload "nvim-refactor.main-test/test-it"}
                        :compiler {:main nvim-refactor.main-test
                                   :output-to "target/out/tests.js"
                                   :output-dir "target/out"
                                   :target :nodejs
                                   :optimizations :none
                                   :source-map true}}]})
