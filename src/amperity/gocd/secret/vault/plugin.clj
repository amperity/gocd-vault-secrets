(ns amperity.gocd.secret.vault.plugin
  "Core plugin implementation."
  (:require
    [amperity.gocd.secret.vault.logging :as log]
    [amperity.gocd.secret.vault.util :as u]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [vault.client.http]
    [vault.core :as vault])
  (:import
    clojure.lang.ExceptionInfo
    (com.thoughtworks.go.plugin.api
      GoApplicationAccessor
      GoPluginIdentifier)
    (com.thoughtworks.go.plugin.api.exceptions
      UnhandledRequestTypeException)
    (com.thoughtworks.go.plugin.api.request
      DefaultGoApiRequest
      GoPluginApiRequest)
    (com.thoughtworks.go.plugin.api.response
      DefaultGoPluginApiResponse
      GoPluginApiResponse)
    java.time.Instant))


;; ## Plugin Initialization

(defn initialize!
  "Set up the vault client."
  [logger app-accessor]
  (alter-var-root #'log/logger (constantly logger))
  (atom nil))


;; ## Model

;; A map of user configurable fields to the all the data necessary to define those fields
(def input-schema
  {;; the name of the corresponding input field
   :vault_addr  {;; the metadata required by the plugin API about every input field
                 :metadata     {:required true :secure false}
                 ;; the string representation of the URL
                 :label        "Vault URL"
                 ;; an array outlining extended validation the function may require
                 :validate-fns [;; nil if no error detected, else a string descring the error
                                #(when-not (or (str/starts-with? % "http://") (str/starts-with? % "https://"))
                                   "Vault URL must start with http:// or https://")]}
   :auth_method {:metadata     {:required true :secure false}
                 :label        "Authentication Method"
                 :validate-fns []}
   :vault_token {:metadata     {:required false :secure true}
                 :label        "Vault Token"
                 :validate-fns [#(when-not (string? %)
                                   "Vault Token must be a string")]}})


;; ## Request Handling

(defmulti handle-request
  "Handle a plugin API request and respond. Methods should return a map containing the following 3 keys:
  ```
  {:response-code     <int: the returned status, follows HTTP status conventions>
   :response-body     <json-coercible: the response body, will be coerced into JSON>
   :response-headers  <map: the response headers, follows HTTP header conventions>}
  ```

  Params:
  - `client`: Atom(vault.client), used for auth and retrieval of all the secret values
  - `req-name`: string, determines how to dispatch among implementing methods, essentially the route
  - `data`: map, the body of the message passed from the GoCD server"
  (fn dispatch
    [client req-name data]
    req-name))


(defmethod handle-request :default
  [_ req-name _]
  (throw (UnhandledRequestTypeException. req-name)))


(defn handler
  "Request handling entry-point."
  [client ^GoPluginApiRequest request]
  (try
    (let [req-name (.requestName request)
          req-data (when-not (str/blank? (.requestBody request))
                     (u/json-decode-map (.requestBody request)))
          {status :response-code body :response-body headers :response-headers} (handle-request client req-name req-data)]
      (DefaultGoPluginApiResponse. status (u/json-encode body) headers))
    (catch UnhandledRequestTypeException ex
      (throw ex))
    (catch Exception ex
      (log/errorx ex "Failed to process %s plugin request%s"
                  (.requestName request)
                  (when-let [data (not-empty (ex-data ex))]
                    (str " " (pr-str data))))
      (DefaultGoPluginApiResponse/error (.getMessage ex)))))


;; ## Plugin Metadata

;; This call is expected to return the icon for the plugin, so as to make
;; it easy for users to identify the plugin.
(defmethod handle-request "go.cd.secrets.get-icon"
  [_ _ _]
  (let [icon-svg (slurp (io/resource "amperity/gocd/secret/vault/logo.svg"))]
    {:response-code    200
     :response-headers {}
     :response-body    {:content_type "image/svg+xml"
                        :data         (u/b64-encode-str icon-svg)}}))


;; ## Secrets Configuration

;; This message should return an HTML template allowing users to configure the
;; secret backend in GoCD.
(defmethod handle-request "go.cd.secrets.secrets-config.get-view"
  [_ _ _]
  (let [view-html (slurp (io/resource "amperity/gocd/secret/vault/secrets-view.html"))]
    {:response-code    200
     :response-headers {}
     :response-body    {:template view-html}}))


;; This message should return metadata about the available settings for
;; configuring a secret backend in GoCD.
(defmethod handle-request "go.cd.secrets.secrets-config.get-metadata"
  [_ _ _]
  {:response-code    200
   :response-headers {}
   :response-body    (mapv (fn [input-key]
                             {:key      input-key
                              :metadata (-> input-schema input-key :metadata)})
                           (keys input-schema))})


(defn- input-error-message
  "If the input field is valid, does nothing, if it's not, returns a string describing the error.

  Params:
  - `field-key`: the input field you are validating
  - `field-value`: the inputted value which you wish to verify"
  [field-key field-value]
  (let [field-model (field-key input-schema)]
    (if (and (-> field-model :metadata :required) (str/blank? field-value))
      ;; field is required but empty
      (str (:label field-model) " is required")
      (when-not (str/blank? field-value)
        (some #(% field-value) (:validate-fns field-model))))))


(defn- authenticate-client-from-inputs!
  "Authenticates the Vault Client.

  Params:
  - `client` The Vault Client you wish to authenticate
  - `inputs` A map containing the user inputted settings for the plugin"
  [client inputs]
  (case (keyword (:auth_method inputs))
    :token
    (vault/authenticate! client :token (:vault_token inputs))

    (throw (ex-info "Unhandled vault auth type"
                    {:user-input (keyword (:auth_method inputs))}))))


;; This call is expected to validate the user inputs that form a part of
;; the secret backend configuration.
(defmethod handle-request "go.cd.secrets.secrets-config.validate"
  [client _ data]
  (let [input-error (fn [field-key]
                      (when-let [error-message (input-error-message field-key (field-key data))]
                        {:key field-key
                         :message error-message}))
        errors-found (keep input-error (keys input-schema))]
    (if (and (empty? errors-found)
             ;; Need to Authenticate?
             (not (and (= (:api-url @client) (:vault_addr data))
                       (= (:auth-type @client) (:auth_method data))
                       (= (:client-token @client) (:vault_token data)))))
      ;; Authenticate Vault client
      (try
        (when (not (= (:api-url @client) (:vault_addr data)))
          (reset! client (vault/new-client (:vault_addr data))))
        (authenticate-client-from-inputs! @client data)
        {:response-code 200
         :response-headers {}
         :response-body []}

        (catch Exception ex
          {:response-code 200
           :response-headers {}
           :response-body [{:key :auth_method
                            :message (str "Unable to authenticate Vault client:\n" ex)}]}))
      {:response-code    200
       :response-headers {}
       :response-body    (into [] errors-found)})))


;; ## Secret Usage

;; This message is a request to the plugin to look up for secrets for a given
;; list of keys. In addition to the list of keys in the JSON request, the
;; request body will also have the configuration required to connect and lookup
;; for secrets from the external Secret Manager.
(defmethod handle-request "go.cd.secrets.secrets-lookup"
  [client _ data]
  (try
    (let [secret-keys (:keys data)
          secrets (mapv (fn [key]
                          {:key key
                           :value (str (vault/read-secret @client key {:not-found nil}))})
                        secret-keys)
          missing-keys (mapv :key (remove :value secrets))]
      (if (empty? missing-keys)
        {:response-code    200
         :response-headers {}
         :response-body    secrets}
        {:response-code    404
         :response-headers {}
         :response-body    {:message (str "Unable to resolve key(s) " missing-keys)}}))
    (catch ExceptionInfo ex
      {:response-code    500
       :response-headers {}
       :response-body    {:message (str "Error occurred during lookup:\n" ex)}})))
