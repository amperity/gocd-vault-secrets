(ns amperity.gocd.secret.vault.plugin-test
  (:require
    [amperity.gocd.secret.vault.plugin :as plugin]
    [clojure.test :refer [testing deftest is]]
    [vault.client.ext.aws :as aws]
    [vault.client.mock]
    [vault.core :as vault]
    [amperity.gocd.secret.vault.util :as u])
  (:import
    (com.thoughtworks.go.plugin.api.request
      DefaultGoPluginApiRequest)
    (com.thoughtworks.go.plugin.api.response
      DefaultGoPluginApiResponse)
    (com.thoughtworks.go.plugin.api.exceptions
      UnhandledRequestTypeException)))


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


(defn mock-client-atom
  "A mock vault client using the secrets found in `resources/secret-fixture.edn`"
  []
  (atom (vault/new-client "mock:amperity/gocd/secret/vault/secret-fixture.edn")))


;; Common Logic Tests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler Tests
(defn default-handler
  "Thunk that sends an empty request, using a a mock client"
  []
  (plugin/handler (mock-client-atom) (default-go-plugin-api-request nil)))


(deftest handler-routing-fake-endpoint
  (testing "Handler when handle-method fails to route to an endpoint correctly"
    (is (thrown-with-msg?
          UnhandledRequestTypeException #"You have chose poorly"
          (plugin/handler (mock-client-atom) (default-go-plugin-api-request "You have chose poorly" nil)))))
  (testing "Handler when fails with some unknown error"
    (with-redefs [plugin/handle-request (fn [_ _ _] (throw (ex-info "this is an error" {:data "stuff"})))]
      (is (response-equal (DefaultGoPluginApiResponse/error "this is an error")
                          (default-handler)))))
  (testing "Handler when handle-method returns a GoPluginApiResponse response"
    (with-redefs [plugin/handle-request (fn [_ _ _] {:response-code 200 :response-body "" :response-headers {}})]
      (is (response-equal (DefaultGoPluginApiResponse/success "\"\"")
                          (default-handler)))))
  (testing "Handler when handle-method returns a GoPluginApiResponse response"
    (with-redefs [plugin/handle-request
                  (fn [_ _ _] {:response-code 200 :response-body {:message "hello"} :response-headers {}})]
      (is (response-equal (DefaultGoPluginApiResponse/success "{\"message\":\"hello\"}")
                          (default-handler)))))
  (testing "Handler when handle-method returns a json response"
    (let [response {:response-code 200 :response-headers {} :response-body {:try "this"}}]
      (with-redefs [plugin/handle-request (fn [_ _ _] response)]
        (is (response-equal (DefaultGoPluginApiResponse/success "{\"try\":\"this\"}")
                            (default-handler)))))))

;; Endpoint Tests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest get-icon
  (testing "Get icon endpoint with well formed requests"
    (let [result (plugin/handle-request (mock-client-atom) "go.cd.secrets.get-icon" "")
          body (:response-body result)
          status (:response-code result)]
      (is "image/svg+xml"
          (:content_type body))
      (is (some? (:data body)))
      (is (= 200 status)))))


(deftest validate
  (testing "Validate correctly handles case with no errors (no false positives)"
    (let [result (plugin/handle-request
                   (mock-client-atom) "go.cd.secrets.secrets-config.validate"
                   {:vault_addr  "https://amperity.com"
                    :auth_method "token"
                    :vault_token "abc123"})
          body (:response-body result)
          status (:response-code result)]
      (is (= [] body))
      (is (= 200 status))))
  (testing "Validate correctly handles case with AWS IAM validation with no errors (no false positives)"
    (with-redefs [vault.client.http/do-api-request (fn [_ _ _] true)
                  vault.client.http/api-auth! (fn [_ _ _] true)]
      (let [result (plugin/handle-request
                     (mock-client-atom)
                     "go.cd.secrets.secrets-config.validate"
                     {:vault_addr      "https://amperity.com"
                      :auth_method     "aws-iam"
                      :iam_role        "role"
                      :aws_credentials (aws/derive-credentials "hello" "goodbye" "7")})
            body (:response-body result)
            status (:response-code result)]
        (is (= [] body))
        (is (= 200 status)))))
  (testing "Validate correctly handles case with errors (no false negatives, no false positives)"
    (let [result (plugin/handle-request
                   (mock-client-atom) "go.cd.secrets.secrets-config.validate"
                   {:vault_addr "protocol://amperity.com"})
          body (:response-body result)
          status (:response-code result)]
      (is (= [{:key     :vault_addr
               :message "Vault URL must start with http:// or https://"}
              {:key     :auth_method
               :message "Authentication Method is required"}]
             body))
      (is (= 200 status))))
  (testing "Validate also resets the vault client if a new URL is specified, when input is valid only"
    (with-redefs [vault.core/new-client
                  (fn [_]
                    (reify vault.core/Client
                      (authenticate! [_ _ _] true)))]
      (let [fake-client (atom nil)
            result (plugin/handle-request
                     fake-client "go.cd.secrets.secrets-config.validate"
                     {:vault_addr  "https://amperity.com"
                      :auth_method "token"
                      :token "defined token"})
            body (:response-body result)
            status (:response-code result)]
        (is (= 200 status))
        (is (= [] body))
        (is (some? @fake-client)))
      (let [fake-client (atom nil)
            result (plugin/handle-request
                     fake-client "go.cd.secrets.secrets-config.validate"
                     {:vault_addr "https://amperity.com"})
            status (:response-code result)]
        (is (= 200 status))
        (is (nil? @fake-client)))))
  (testing "Validate a does not reset the vault client if no new URL is specified "
    (let [fake-client (atom nil)
          result (plugin/handle-request
                   fake-client "go.cd.secrets.secrets-config.validate"
                   {})
          status (:response-code result)]
      (is (= 200 status))
      (is (nil? @fake-client))))
  (testing "Validate also returns client authentication errors"
    (let [fake-client (atom nil)
          result (plugin/handle-request
                   fake-client "go.cd.secrets.secrets-config.validate"
                   {:vault_addr  "https://amperity.com"
                    :auth_method "token"})
          body (:response-body result)
          status (:response-code result)]
      (is (= 200 status))
      (is (= [{:key     :auth_method
               :message "Unable to authenticate Vault client:
java.lang.IllegalArgumentException: Token credential must be a string"}]
             body))
      (is (some? @fake-client)))
    (let [fake-client (atom nil)
          result (plugin/handle-request
                   fake-client "go.cd.secrets.secrets-config.validate"
                   {:vault_addr  "https://amperity.com"
                    :auth_method "fake-id-mclovin"})
          body (:response-body result)
          status (:response-code result)]
      (is (= 200 status))
      (is (= [{:key     :auth_method
               :message "Unable to authenticate Vault client:
clojure.lang.ExceptionInfo: Unhandled vault auth type {:user-input \"fake-id-mclovin\"}"}]
             body))
      (is (some? @fake-client)))))


(deftest get-metadata
  (testing "Input metadata is returned with the correctly structured response"
    (let [result (plugin/handle-request (mock-client-atom) "go.cd.secrets.secrets-config.get-metadata" {})
          body (:response-body result)
          status (:response-code result)]
      (is (= 200 status))
      (is (not (empty? body)))
      (is (every? :key body))
      (is (every? #(and (contains? % :required) (contains? % :secure)) (map :metadata body))))))


(deftest secrets-lookup
  (testing "If client is not defined, it gets defined and results are returned as expected"
    (with-redefs [plugin/authenticate-client-from-inputs!
                  (fn [client _]
                    (reset! client @(mock-client-atom)))]
      (let [client (atom nil)
            result (plugin/handle-request
                     client
                     "go.cd.secrets.secrets-lookup"
                     {:configuration {}
                      ;; The keys will likely be string in the http vault client instance,
                      ;; but this is easier for testing.
                      :keys          ["identities#batman" "identities#hulk" "identities#wonder-woman"]})
            body (:response-body result)
            status (:response-code result)]
        (is (= [{:key "identities#batman" :value "Bruce Wayne"}
                {:key "identities#hulk" :value "Bruce Banner"}
                {:key "identities#wonder-woman" :value "Diana Prince"}]
               body))
        (is (= 200 status))
        (is (some? @client)))))
  (testing "If client is not defined and authentication fails, lookup fails cleanly"
    (let [client (atom nil)
          result (plugin/handle-request
                   client
                   "go.cd.secrets.secrets-lookup"
                   {:configuration {}
                    ;; The keys will likely be string in the http vault client instance,
                    ;; but this is easier for testing.
                    :keys          ["identities#batman" "identities#hulk" "identities#wonder-woman"]})
          body (:response-body result)
          status (:response-code result)]
      (is (= {:message "Error occurred during lookup of: [\"identities#batman\" \"identities#hulk\" \"identities#wonder-woman\"]
clojure.lang.ExceptionInfo: Unhandled vault auth type {:user-input nil}"}
             body))
      (is (= 500 status))
      (is (nil? @client))))
  (testing "Can look up individual keys stored in vault given a well formed request"
    (let [result (plugin/handle-request
                   (mock-client-atom)
                   "go.cd.secrets.secrets-lookup"
                   {:configuration {}
                    ;; The keys will likely be string in the http vault client instance,
                    ;; but this is easier for testing.
                    :keys          ["identities#batman" "identities#hulk" "identities#wonder-woman"]})
          body (:response-body result)
          status (:response-code result)]
      (is (= [{:key "identities#batman" :value "Bruce Wayne"}
              {:key "identities#hulk" :value "Bruce Banner"}
              {:key "identities#wonder-woman" :value "Diana Prince"}]
             body))
      (is (= 200 status))))
  (testing "Fails cleanly when looking up secrets that don't exist"
    (let [result (plugin/handle-request (mock-client-atom) "go.cd.secrets.secrets-lookup"
                                        {:configuration {}
                                         :keys          ["identities#dr-who" "identities#jack-the-ripper"]})
          body (:response-body result)
          status (:response-code result)]
      (is (= {:message "Unable to resolve key(s) [\"identities#dr-who\" \"identities#jack-the-ripper\"]"}
             body))
      (is (= 404 status))))
  (testing "Fails cleanly when other lookup error occurs"
    (let [mock-client-that-errors (reify vault.core/SecretClient
                                    (read-secret [_ _ _] (throw (ex-info "Mock Exception" {}))))
          result (plugin/handle-request
                   (atom mock-client-that-errors)
                   "go.cd.secrets.secrets-lookup"
                   {:configuration {}
                    :keys          ["identities#batman"]})
          body (:response-body result)
          status (:response-code result)]
      (is (= {:message "Error occurred during lookup of: [\"identities#batman\"]
clojure.lang.ExceptionInfo: Mock Exception {}"}
             body))
      (is (= 500 status)))))
