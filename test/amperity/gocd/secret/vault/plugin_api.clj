(ns amperity.gocd.secret.vault.plugin-api
  (:require
    [amperity.gocd.secret.vault.plugin :as plugin]
    [amperity.gocd.secret.vault.util :as u]
    [clojure.test :refer [testing deftest is]]
    [vault.client.mock]
    [vault.core :as vault])
  (:import
    (com.thoughtworks.go.plugin.api.request
      DefaultGoPluginApiRequest)
    (com.thoughtworks.go.plugin.api.response
      DefaultGoPluginApiResponse)))


;; Utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn default-go-plugin-api-request
  "A .DefaultGoPluginApiRequest with the given name and params.
  Parameters:
  - `request-name` string of the name (essentially the route) of the request
  - `request-params` map of request parameters"
  ([request-name request-params]
   (doto (DefaultGoPluginApiRequest. nil nil request-name)
     (.setRequestParams request-params)))
  ([request-name]
   (default-go-plugin-api-request request-name {})))


(defn response-equal
  "Determines if the given responses are equal enough to be treated the same by the GoCD server.
  Parameters:
  - `response1`: .GoPluginApiResponse to compare to response2
  - `response2`: .GoPluginApiResponse to compare to response1"
  [response1 response2]
  ;; Uncomment these print statements for debugging
  ;(println (u/json-encode response1))
  ;(println (u/json-encode response2))
  (and (= (.responseCode response1) (.responseCode response2))
       (= (.responseBody response1) (.responseBody response2))
       (= (.responseHeaders response1) (.responseHeaders response2))))


(defn mock-client
  "A mock vault client using the secrets found in `resources/secret-fixture.edn`"
  []
  (vault/new-client "mock:resources/secret-fixture.edn"))

;; Common Logic Tests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler Tests
(deftest handler-with-nil-response
  (testing "Handler when handle-method returns a GoPluginApiResponse response"
    (with-redefs [plugin/handle-request (fn [_ _ _] {:response-code 200 :response-body "" :response-headers {}})]
      (is (response-equal (DefaultGoPluginApiResponse/success "\"\"")
                          (plugin/handler (mock-client) (default-go-plugin-api-request nil)))))))


(deftest handler-with-plugin-response
  (testing "Handler when handle-method returns a GoPluginApiResponse response"
    (with-redefs [plugin/handle-request
                  (fn [_ _ _] {:response-code 200 :response-body {:message "hello"} :response-headers {}})]
      (is (response-equal (DefaultGoPluginApiResponse/success "{\"message\":\"hello\"}")
                          (plugin/handler (mock-client) (default-go-plugin-api-request nil)))))))


(deftest handler-with-json-response
  (testing "Handler when handle-method returns a json response"
    (let [response {:response-code 200 :response-headers {} :response-body {:try "this"}}]
      (with-redefs [plugin/handle-request (fn [_ _ _] response)]
        (is (response-equal (DefaultGoPluginApiResponse/success "{\"try\":\"this\"}")
                            (plugin/handler (mock-client) (default-go-plugin-api-request nil))))))))

;; Endpoint Tests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest get-icon
  (testing "Get icon endpoint with well formed requests"
    (let [result (plugin/handle-request (mock-client) "cd.go.secrets.get-icon" "")
          body (:response-body result)
          status (:response-code result)
          _ (:response-headers result)]
      (is "image/svg+xml"
          (:content_type body))
      (is (:data body))
      (is (= status 200)))))


(deftest secrets-lookup
  (testing "Can look up secrets stored in vault given a well formed request"
    (let [result (plugin/handle-request
                   (mock-client)
                   "go.cd.secrets.secrets-lookup"
                   {:configuration {}
                    ;; The keys will likely be string in the http vault client instance,
                    ;; but this is easier for testing.
                    :keys          [:batman :hulk :wonder-woman]})
          body (:response-body result)
          status (:response-code result)
          _ (:response-headers result)]
      (is (= [{:key :batman :value "Bruce Wayne"}
              {:key :hulk :value "Bruce Banner"}
              {:key :wonder-woman :value "Diana Prince"}]
             body))
      (is (= status 200))))
  (testing "Fails cleanly when looking up secrets that don't exist"
    (let [result (plugin/handle-request (mock-client) "go.cd.secrets.secrets-lookup"
                                        {:configuration {}
                                         :keys          [:dr-who :jack-the-ripper]})
          body (:response-body result)
          status (:response-code result)
          _ (:response-headers result)]
      (is (= {:message "Unable to resolve key :dr-who"}
             body))
      (is (= status 404))))
  (testing "Fails cleanly when other lookup error occurs"
    (let [result (with-redefs [vault.core/read-secret (fn [& _] (throw (ex-info "Mock Exception" {})))]
                   (plugin/handle-request nil "go.cd.secrets.secrets-lookup"
                                          {:configuration {}
                                           :keys          [:batman]}))
          body (:response-body result)
          status (:response-code result)
          _ (:response-headers result)]
      (is (= body {:message "Error occurred during lookup:\n clojure.lang.ExceptionInfo: Mock Exception {}"}))
      (is (= status 500)))))
