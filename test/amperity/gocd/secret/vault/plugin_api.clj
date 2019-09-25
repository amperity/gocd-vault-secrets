(ns amperity.gocd.secret.vault.plugin-api
  (:require [clojure.test :refer :all]
            [amperity.gocd.secret.vault.plugin :as plugin]
            [amperity.gocd.secret.vault.util :as u])
  (:import (com.thoughtworks.go.plugin.api.request DefaultGoPluginApiRequest)
           (com.thoughtworks.go.plugin.api.response DefaultGoPluginApiResponse)))

;; Utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; https://github.com/gocd/gocd/blob/master/plugin-infra/go-plugin-api/src/main/java/com/thoughtworks/go/plugin/api/request/DefaultGoPluginApiRequest.java
(defn default-go-plugin-api-request
  "A .DefaultGoPluginApiRequest with the given name and params"
  ([request-name request-params] ;; string map -> .DefaultGoPluginApiRequest
   (doto (DefaultGoPluginApiRequest. nil nil request-name)
     (.setRequestParams request-params)))
  ([request-name] ;; string -> .DefaultGoPluginApiRequest
   (default-go-plugin-api-request request-name {:set "yes"})))


(defn has-response-quality
  "Asserts that the appropriate response is served by the plugin

  - `request`: the request given to the plugin
  - `response-quality?`: determines if the response has the given quality"
  [request response-quality? & response-qualities]  ;; .DefaultGoPluginApiRequest (.DefaultGoPluginApiResponse -> bool) -> nil
  (testing (str "Testing request with name " (.requestName request))
    (let [response (plugin/handler (atom {}) request)]
      (is (response-quality? response))
      (run! #(is (% response)) response-qualities))))

;; Tests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest get-icon
  (has-response-quality
    (default-go-plugin-api-request "cd.go.secrets.get-icon")
    #(= (.responseCode %) 200)
    #(:data (u/json-decode-map (.responseBody %)))  ;; Check data field exists
    #(= (:content_type (u/json-decode-map (.responseBody %))) "image/svg+xml")))
