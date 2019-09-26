(ns amperity.gocd.secret.vault.plugin-api
  (:require
    [amperity.gocd.secret.vault.plugin :as plugin]
    [clojure.test :refer [testing deftest is]])
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
  (and (= (.responseCode response1) (.responseCode response2))
       (= (.responseBody response1) (.responseBody response2))
       (= (.responseHeaders response1) (.responseHeaders response2))))


;; Common Logic Tests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler Tests
(deftest handler-with-nil-response
  (testing "Handler when handle-method returns a GoPluginApiResponse response"
    (with-redefs [plugin/handle-request (fn [_ _ _] true)]
      (is (response-equal (DefaultGoPluginApiResponse/success "")
                          (plugin/handler (atom {}) (default-go-plugin-api-request nil)))))))


(deftest handler-with-plugin-response
  (testing "Handler when handle-method returns a GoPluginApiResponse response"
    (let [response (DefaultGoPluginApiResponse. 200 "response body")]
      (with-redefs [plugin/handle-request (fn [_ _ _] response)]
        (is (= response
               (plugin/handler (atom {}) (default-go-plugin-api-request nil))))))))


(deftest handler-with-json-response
  (testing "Handler when handle-method returns a json response"
    (let [response {:try "this"}]
      (with-redefs [plugin/handle-request (fn [_ _ _] response)]
        (is (response-equal (DefaultGoPluginApiResponse/success "{\"try\":\"this\"}")
                            (plugin/handler (atom {}) (default-go-plugin-api-request nil))))))))

;; Endpoint Tests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest get-icon
  (testing "Get icon endpoint with well formed requests"
    (let [result-empty-body (plugin/handle-request (atom {}) "cd.go.secrets.get-icon" "")]
      (is "image/svg+xml"
          (:content_type result-empty-body))
      (is (:data result-empty-body)))))
