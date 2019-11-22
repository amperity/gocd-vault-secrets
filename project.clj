(defproject amperity/gocd-vault-secrets "0.3.2-SNAPSHOT"
  :description "A plugin for GoCD providing secret material support via HashiCorp Vault."
  :url "https://github.com/amperity/gocd-vault-secrets"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :plugins
  [[lein-cloverage "1.1.0"]]

  :dependencies
  [[amperity/vault-clj "0.7.1"]
   [amperity/vault-clj-aws "0.0.2"]
   [com.google.code.gson/gson "2.8.5"]
   [org.clojure/clojure "1.10.1"]]

  :java-source-paths ["src"]

  :hiera
  {:cluster-depth 4
   :vertical false
   :show-external false}

  :profiles
  {:provided
   {:dependencies
    [[cd.go.plugin/go-plugin-api "19.7.0"]
     [com.google.guava/guava "23.0"]]}

   :repl
   {:source-paths ["dev"]
    :dependencies
    [[org.clojure/tools.namespace "0.2.11"]]}

   :uberjar
   {:target-path "target/uberjar"
    :aot :all}})
