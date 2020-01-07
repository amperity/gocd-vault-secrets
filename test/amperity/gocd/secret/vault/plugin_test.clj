(ns amperity.gocd.secret.vault.plugin-test
  (:require
    [amperity.gocd.secret.vault.logging :as log]
    [amperity.gocd.secret.vault.plugin :as plugin]
    [amperity.gocd.secret.vault.util :as u]
    [clojure.test :refer [testing deftest is]]
    [vault.client.ext.aws :as aws]
    [vault.client.mock]
    [vault.core :as vault])
  (:import
    (com.thoughtworks.go.plugin.api.exceptions
      UnhandledRequestTypeException)
    (com.thoughtworks.go.plugin.api.request
      DefaultGoPluginApiRequest)
    (com.thoughtworks.go.plugin.api.response
      DefaultGoPluginApiResponse)))


;; Utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn default-go-plugin-api-request
  "A .DefaultGoPluginApiRequest with the given name and params.
  Parameters:
  - `request-name`: string of the name (essentially the route) of the request
  - `request-params`: map of request parameters
  - `request-body`: Json formatted request body represented as string"
  ([request-name request-params request-body]
   (doto (DefaultGoPluginApiRequest. nil nil request-name)
     (.setRequestParams request-params)
     (.setRequestBody request-body)))
  ([request-name request-params]
   (default-go-plugin-api-request request-name request-params nil))
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
  (vault/new-client "mock:amperity/gocd/secret/vault/secret-fixture.edn"))


;; Common Logic Tests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler Tests
(defn default-handler
  "Thunk that sends an empty request, using a a mock client"
  []
  (plugin/handler (default-go-plugin-api-request nil)))


(deftest handler-routing-fake-endpoint
  (testing "Handler when handle-method fails to route to an endpoint correctly"
    (is (thrown-with-msg?
          UnhandledRequestTypeException #"You have chose poorly"
          (plugin/handler (default-go-plugin-api-request "You have chose poorly" {:totally "here"})))))
  (testing "Handler when fails with some unknown error"
    (with-redefs [plugin/handle-request (fn [_ _] (throw (ex-info "this is an error" {})))
                  log/errorx (fn [_ _ _ _] nil)]
      (is (response-equal (DefaultGoPluginApiResponse/error "this is an error")
                          (default-handler)))))
  (testing "Handler when handle-method returns a GoPluginApiResponse response"
    (with-redefs [plugin/handle-request (fn [_ _] {:response-code 200 :response-body "" :response-headers {}})]
      (is (response-equal (DefaultGoPluginApiResponse/success "\"\"")
                          (default-handler)))))
  (testing "Handler when handle-method returns a GoPluginApiResponse response"
    (with-redefs [plugin/handle-request
                  (fn [_ _] {:response-code 200 :response-body {:message "hello"} :response-headers {}})]
      (is (response-equal (DefaultGoPluginApiResponse/success "{\"message\":\"hello\"}")
                          (default-handler)))))
  (testing "Handler when handle-method returns a json response"
    (let [response {:response-code 200 :response-headers {} :response-body {:try "this"}}]
      (with-redefs [plugin/handle-request (fn [_ _] response)]
        (is (response-equal (DefaultGoPluginApiResponse/success "{\"try\":\"this\"}")
                            (default-handler))))))
  (testing "Handler when request includes a JSON body and handle-method returns a json response"
    (let [response {:response-code 200 :response-headers {} :response-body {:try "this"}}]
      (with-redefs [plugin/handle-request (fn [_ _] response)]
        (is (response-equal (DefaultGoPluginApiResponse/success "{\"try\":\"this\"}")
                            (plugin/handler
                              (default-go-plugin-api-request "ignored" {}  (u/json-encode {:try "this"
                                                                                           :and ["a" "vec"]
                                                                                           "or" {:another "map"}
                                                                                           :maybe #{1 2 3}})))))))))


;; Endpoint Tests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest get-icon
  (testing "Get icon endpoint with well formed requests"
    (let [result (plugin/handle-request "go.cd.secrets.get-icon" "")
          body (:response-body result)
          status (:response-code result)]
      (is "image/svg+xml"
          (:content_type body))
      (is (some? (:data body)))
      (is (= 200 status)))))


(deftest get-view
  (testing "Get view endpoint with well formed requests"
    (let [result (plugin/handle-request "go.cd.secrets.secrets-config.get-view" "")
          body (:response-body result)
          status (:response-code result)]
      (is (some? (:template body)))
      (is (= 200 status)))))


(deftest validate
  (testing "Validate correctly handles case with no errors (no false positives)"
    (let [result (plugin/handle-request
                   "go.cd.secrets.secrets-config.validate"
                   {:vault_addr  "https://amperity.com"
                    :auth_method "token"
                    :vault_token "abc123"
                    :force_read  "true"})
          body (:response-body result)
          status (:response-code result)]
      (is (= [] body))
      (is (= 200 status))))
  (testing "Validate correctly handles case with AWS IAM validation with no errors (no false positives)"
    (with-redefs [vault.client.http/do-api-request (fn [_ _ _] true)
                  vault.client.http/api-auth! (fn [_ _ _] true)]
      (let [result (plugin/handle-request
                     "go.cd.secrets.secrets-config.validate"
                     {:vault_addr      "https://amperity.com"
                      :auth_method     "aws-iam"
                      :iam_role        "role"
                      :aws_credentials (aws/derive-credentials "hello" "goodbye" "7")
                      :force_read "false"})
            body (:response-body result)
            status (:response-code result)]
        (is (= [] body))
        (is (= 200 status)))))
  (testing "Validate correctly handles case with errors (no false negatives, no false positives)"
    (let [result (plugin/handle-request
                   "go.cd.secrets.secrets-config.validate"
                   {:vault_addr "protocol://amperity.com"
                    :force_read "false"})
          body (:response-body result)
          status (:response-code result)]
      (is (= [{:key     :vault_addr
               :message "Vault URL must start with http:// or https://"}
              {:key     :auth_method
               :message "Authentication Method is required"}]
             body))
      (is (= 200 status))))
  (testing "Validate also returns client authentication errors"
    (let [result (plugin/handle-request
                   "go.cd.secrets.secrets-config.validate"
                   {:vault_addr  "https://amperity.com"
                    :auth_method "token"
                    :force_read "false"})
          body (:response-body result)
          status (:response-code result)]
      (is (= 200 status))
      (is (= [{:key     :auth_method
               :message "Unable to authenticate Vault client:
java.lang.IllegalArgumentException: Token credential must be a string"}]
             body)))
    (let [result (plugin/handle-request
                   "go.cd.secrets.secrets-config.validate"
                   {:vault_addr  "https://amperity.com"
                    :auth_method "fake-id-mclovin"
                    :force_read "true"})
          body (:response-body result)
          status (:response-code result)]
      (is (= 200 status))
      (is (= [{:key     :auth_method
               :message "Unable to authenticate Vault client:
clojure.lang.ExceptionInfo: Unhandled vault auth type {:user-input \"fake-id-mclovin\"}"}]
             body)))))


(deftest get-metadata
  (testing "Input metadata is returned with the correctly structured response"
    (let [result (plugin/handle-request "go.cd.secrets.secrets-config.get-metadata" {})
          body (:response-body result)
          status (:response-code result)]
      (is (= 200 status))
      (is (not (empty? body)))
      (is (every? :key body))
      (is (every? #(and (contains? % :required) (contains? % :secure)) (map :metadata body))))))


(deftest secrets-lookup
  (with-redefs [plugin/client-from-inputs
                (fn [_]
                  (mock-client))]
    (testing "If authentication fails, lookup fails cleanly"
      (let [result (plugin/handle-request
                     "go.cd.secrets.secrets-lookup"
                     {:configuration {}
                      :keys          ["identities#batman" "identities#hulk" "identities#wonder-woman"]})
            body (:response-body result)
            status (:response-code result)]
        (is (= {:message "Error occurred during lookup of: [\"identities#batman\" \"identities#hulk\" \"identities#wonder-woman\"]
clojure.lang.ExceptionInfo: Unhandled vault auth type {:user-input nil}"}
               body))
        (is (= 500 status))))
    (with-redefs [plugin/authenticate-client-from-inputs!
                  (fn [_ _] nil)]
      (testing "Results are returned as expected"
        (let [result (plugin/handle-request
                       "go.cd.secrets.secrets-lookup"
                       {:configuration {}
                        :keys          ["identities#batman" "identities#hulk" "identities#wonder-woman"]})
              body (:response-body result)
              status (:response-code result)]
          (is (= [{:key "identities#batman" :value "Bruce Wayne"}
                  {:key "identities#hulk" :value "Bruce Banner"}
                  {:key "identities#wonder-woman" :value "Diana Prince"}]
                 body))
          (is (= 200 status))))
      (testing "Can look up individual keys stored in vault given a well formed request"
        (let [result (plugin/handle-request
                       "go.cd.secrets.secrets-lookup"
                       {:configuration {}
                        :keys          ["identities#batman" "identities#hulk" "identities#wonder-woman"]})
              body (:response-body result)
              status (:response-code result)]
          (is (= [{:key "identities#batman" :value "Bruce Wayne"}
                  {:key "identities#hulk" :value "Bruce Banner"}
                  {:key "identities#wonder-woman" :value "Diana Prince"}]
                 body))
          (is (= 200 status))))
      (testing "Can force override cache when configured to"
        (let [client (mock-client)]
          (with-redefs [plugin/client-from-inputs
                        (fn [_] client)]
            (let [orig-result (plugin/handle-request
                                "go.cd.secrets.secrets-lookup"
                                {:keys          ["identities#batman" "identities#hulk" "identities#wonder-woman"]})]
              (is (= [{:key "identities#batman" :value "Bruce Wayne"}
                      {:key "identities#hulk" :value "Bruce Banner"}
                      {:key "identities#wonder-woman" :value "Diana Prince"}]
                     (:response-body orig-result)))
              (vault/write-secret! client "identities" {:batman "Wayne, Bruce"
                                                        :hulk "Banner, Bruce"
                                                        :wonder-woman "Prince, Diana"})
              (is (= [{:key "identities#batman" :value "Wayne, Bruce"}
                      {:key "identities#hulk" :value "Banner, Bruce"}
                      {:key "identities#wonder-woman" :value "Prince, Diana"}]
                     (:response-body
                       (plugin/handle-request
                         "go.cd.secrets.secrets-lookup"
                         {:configuration {:force_read "true"}
                          :keys          ["identities#batman" "identities#hulk" "identities#wonder-woman"]}))))))))
      (testing "Fails cleanly when looking up secrets that don't exist"
        (let [result (plugin/handle-request "go.cd.secrets.secrets-lookup"
                                            {:configuration {}
                                             :keys          ["identities#dr-who" "identities#jack-the-ripper"]})
              body (:response-body result)
              status (:response-code result)]
          (is (= {:message "Unable to resolve key(s) [\"identities#dr-who\" \"identities#jack-the-ripper\"]"}
                 body))
          (is (= 404 status))))
      (testing "Fails cleanly when other lookup error occurs"
        (let [mock-client-that-errors (reify vault.core/SecretClient
                                        (read-secret [_ _ _] (throw (ex-info "Mock Exception" {}))))]
          (with-redefs [plugin/client-from-inputs
                        (fn [_] mock-client-that-errors)]
            (let [result (plugin/handle-request
                           "go.cd.secrets.secrets-lookup"
                           {:configuration {}
                            :keys          ["identities#batman"]})
                  body (:response-body result)
                  status (:response-code result)]
              (is (= {:message "Error occurred during lookup of: [\"identities#batman\"]
clojure.lang.ExceptionInfo: Mock Exception {}"}
                     body))
              (is (= 500 status))))))
      (testing "Can lookup token when specified without policies"
        (let [result (plugin/handle-request
                       "go.cd.secrets.secrets-lookup"
                       {:configuration {}
                        :keys          ["TOKEN:"]})
              body (:response-body result)
              status (:response-code result)]
          (is (= "TOKEN:" (:key (first body))))
          (is (and (string? (:value (first body))) (pos-int? (count (:value (first body))))))
          (is (= 200 status))))
      (testing "Can lookup token when specified with policies"
        (let [result (plugin/handle-request
                       "go.cd.secrets.secrets-lookup"
                       {:configuration {}
                        :keys          ["TOKEN:1,2,3"]})
              body (:response-body result)
              status (:response-code result)]
          (is (= "TOKEN:1,2,3" (:key (first body))))
          (is (and (string? (:value (first body))) (pos-int? (count (:value (first body))))))
          (is (= 200 status))))
      (testing "Can look up individual keys stored in vault given a well formed request"
        (let [result (plugin/handle-request
                       "go.cd.secrets.secrets-lookup"
                       {:configuration {}
                        :keys          ["TOKEN:1,2" "TOKEN:" "identities#batman" "identities#hulk" "identities#wonder-woman"]})
              body (:response-body result)
              status (:response-code result)]
          (is (= "TOKEN:1,2" (:key (first body))))
          (is (and (string? (:value (first body))) (pos-int? (count (:value (first body))))))
          (is (= "TOKEN:" (:key (second body))))
          (is (and (string? (:value (second body))) (pos-int? (count (:value (second body))))))
          (is (= [{:key "identities#batman" :value "Bruce Wayne"}
                  {:key "identities#hulk" :value "Bruce Banner"}
                  {:key "identities#wonder-woman" :value "Diana Prince"}]
                 (subvec body 2)))
          (is (= 200 status)))))))
