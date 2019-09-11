(ns amperity.gocd.secret.vault.plugin
  "Core plugin implementation."
  (:require
    [amperity.gocd.secret.vault.logging :as log]
    [amperity.gocd.secret.vault.util :as u]
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
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
  "Initialize the plugin state."
  [logger app-accessor]
  (alter-var-root #'log/logger (constantly logger))
  ;; TODO: determine what goes in the state
  (atom {}))



;; ## Request Handling

(defmulti handle-request
  "Handle a plugin API request and respond. Methods should return `true` for an
  empty success response, a data structure to coerce into a successful JSON
  response, or a custom `GoPluginApiResponse`."
  (fn dispatch
    [state req-name data]
    req-name))


(defmethod handle-request :default
  [_ req-name _]
  (throw (UnhandledRequestTypeException. req-name)))


(defn handler
  "Request handling entry-point."
  [state ^GoPluginApiRequest request]
  (try
    (let [req-name (.requestName request)
          req-data (when-not (str/blank? (.requestBody request))
                     (u/json-decode-map (.requestBody request)))
          result (handle-request state req-name req-data)]
      (cond
        (true? result)
        (DefaultGoPluginApiResponse/success "")

        (instance? GoPluginApiResponse result)
        result

        :else
        (DefaultGoPluginApiResponse/success (u/json-encode result))))
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
(defmethod handle-request "cd.go.secrets.get-icon"
  [_ _ _]
  (let [icon-svg (slurp (io/resource "amperity/gocd/secret/vault/logo.svg"))]
    {:content_type "image/svg+xml"
     :data (u/b64-encode-str icon-svg)}))



;; ## Secrets Configuration

;; This message should return an HTML template allowing users to configure the
;; secret backend in GoCD.
(defmethod handle-request "go.cd.secrets.get-view"
  [_ _ _]
  (let [view-html (slurp (io/resource "amperity/gocd/secret/vault/secrets-view.html"))]
    {:template view-html}))


;; This message should return metadata about the available settings for
;; configuring a secret backend in GoCD.
(defmethod handle-request "go.cd.secrets.get-metadata"
  [_ _ _]
  ;; TODO: how does the plugin authenticate?
  [{:key :vault_addr
    :metadata {:required true, :secure false}}])


;; This call is expected to validate the user inputs that form a part of
;; the secret backend configuration.
(defmethod handle-request "go.cd.screts.validate"
  [_ _ data]
  (letfn [(validate-string
            [field-key label]
            (when (str/blank? (get data field-key))
              {:key field-key
               :message (str label " is required")}))]
    (into
      []
      (remove nil?)
      [(validate-string :vault_addr "Vault URL")])))



;; ## Secret Usage

;; This message is a request to the plugin to look up for secrets for a given
;; list of keys. In addition to the list of keys in the JSON request, the
;; request body will also have the configuration required to connect and lookup
;; for secrets from the external Secret Manager.
(defmethod handle-request "go.cd.secrets.secrets-lookup"
  [_ _ data]
  (let [configuration (:configuration data)
        secret-keys (:keys data)]
    ;; TODO: See https://plugin-api.gocd.org/19.7.0/secrets/#lookup-secrets for desired response
    (DefaultGoPluginApiResponse/error "NYI")))
