(defproject lambda-email-capture "0.1.0-SNAPSHOT"
  :description
  "An AWS lambda function which saves emails into a Datomic database"

  :url
  "https://github.com/PennyProfit/lambda-email-capture"

  :dependencies
  [[cljsjs/nodejs-externs "1.0.4-1"]
   [figwheel-sidecar "0.5.2"]
   [io.nervous/cljs-lambda "0.3.0"]
   [oscar-marshall/datomic-cljs "0.0.1-alpha-1"]
   [org.clojure/clojure "1.8.0"]
   [org.clojure/clojurescript "1.8.40"]
   [org.clojure/core.async "0.2.374"]
   [prismatic/schema "1.1.0"]]

  :plugins
  [[io.nervous/lein-cljs-lambda "0.5.1"]
   [lein-cljsbuild "1.1.3"]
   [lein-doo "0.1.7-SNAPSHOT"]
   [lein-npm "0.6.2"]]

  :npm
  {:dependencies [[request "2.71.0"]
                  [source-map-support "0.4.0"]
                  [ws "1.0.1"]]}

  :source-paths
  ["src"]

  :cljs-lambda
  {:defaults  {:role "arn:aws:iam::635195531909:role/cljs-lambda-default"}
   :functions [{:name   "email-capture"
                :invoke lambda-email-capture.core/email-capture-lambda}]}

  :cljsbuild
  {:builds [{:id           "prod"
             :source-paths ["src"]
             :compiler     {:output-to     "target/prod/main.js"
                            :output-dir    "target/prod"
                            :source-map    "target/prod/main.js.map"
                            :target        :nodejs
                            :language-in   :ecmascript5
                            :optimizations :advanced}}
            {:id           "dev"
             :source-paths ["src"]
             :figwheel     true
             :compiler     {:output-to   "target/dev/index.js"
                            :output-dir  "target/dev"
                            :source-map  true
                            :target      :nodejs
                            :language-in :ecmascript5
                            :main        lambda-email-capture.core}}
            {:id           "test"
             :source-paths ["src" "test"]
             :compiler     {:output-to   "target/test/index.js"
                            :output-dir  "target/test"
                            :target      :nodejs
                            :language-in :ecmascript5
                            :main        lambda-email-capture.test-runner}}]}

  :doo {:build "test"})
