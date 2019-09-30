(ns amperity.gocd.secret.vault.plugin
  "Core plugin implementation."
  (:require
    [amperity.gocd.secret.vault.logging :as log]
    [amperity.gocd.secret.vault.util :as u]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [vault.client.http]
    [vault.client.mock]
    [vault.core :as vault])
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

;; (vault/renew-token client)

(defn initialize!
  ;; should really just be named initialize, not initialize!, all mutation is self contained
  ; 1. Access app info using app accessor to determine url, app-id, etc.
  ; 2. Create a vault client
  ; 3. Authenticate the vault client
  ; 4. Return the vault client
  "Set up the vault client."
  [logger app-accessor]
  (alter-var-root #'log/logger (constantly logger))
  ;; TODO: actually initialize a Vault client
  (atom {}))


;; ## Request Handling

(defmulti handle-request
  "Handle a plugin API request and respond. Methods should return a map containing the following 3 keys:
  ```{:response-code     <int: the returned status, follows HTTP status conventions>
      :response-body     <json-coercible: the response body, will be coerced into JSON>
      :response-headers  <map: the response headers, follows HTTP header conventions>}```

  Params:
  - `client`: vault.client, used for auth and retrieval of all the secret values
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
(defmethod handle-request "cd.go.secrets.get-icon"
  [_ _ _]
  (let [icon-svg (slurp (io/resource "amperity/gocd/secret/vault/logo.svg"))]
    {:response-code    200
     :response-headers {}
     :response-body    {:content_type "image/svg+xml"
                        :data         (u/b64-encode-str icon-svg)}}))


;; ## Secrets Configuration

;; This message should return an HTML template allowing users to configure the
;; secret backend in GoCD.
(defmethod handle-request "go.cd.secrets.get-view"
  [_ _ _]
  (let [view-html (slurp (io/resource "amperity/gocd/secret/vault/secrets-view.html"))]
    {:response-code    200
     :response-headers {}
     :response-body    {:template view-html}}))


;; This message should return metadata about the available settings for
;; configuring a secret backend in GoCD.
(defmethod handle-request "go.cd.secrets.get-metadata"
  [_ _ _]
  ;; TODO: how does the plugin authenticate?
  {:response-code    200
   :response-headers {}
   :response-body    [{:key      :vault_addr
                       :metadata {:required true :secure false}}]})


;; This call is expected to validate the user inputs that form a part of
;; the secret backend configuration.
(defmethod handle-request "go.cd.screts.validate"
  [_ _ data]
  (letfn [(validate-string
            [field-key label]
            (when (str/blank? (get data field-key))
              {:key     field-key
               :message (str label " is required")}))]
    {:response-code    200
     :response-headers {}
     :response-body    (into [] (remove nil?) [(validate-string :vault_addr "Vault URL")])}))


;; ## Secret Usage

;; This message is a request to the plugin to look up for secrets for a given
;; list of keys. In addition to the list of keys in the JSON request, the
;; request body will also have the configuration required to connect and lookup
;; for secrets from the external Secret Manager.
(defmethod handle-request "go.cd.secrets.secrets-lookup"
  [client _ data]
  (let [configuration (:configuration data)
        secret-keys (:keys data)
        error-during-lookup (fn [e]
                              {:response-code    500
                               :response-headers {}
                               :response-body    {:message (str "Error occurred during lookup:\n " e)}})]
    (try
      {:response-code    200
       :response-headers {}
       :response-body    (into [] (map (fn [key] {:key key :value (vault/read-secret client key)}) secret-keys))}
      (catch clojure.lang.ExceptionInfo ex
        (if (= "No such secret" (first (str/split (ex-message ex) #":")))
          {:response-code    404
           :response-headers {}
           :response-body    {:message (str "Unable to resolve key(s) " secret-keys)}}
          (error-during-lookup ex)))
      (catch Exception e
        (error-during-lookup e)))))
