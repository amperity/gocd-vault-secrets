package amperity.gocd.secret.vault;

import clojure.java.api.Clojure;
import clojure.lang.Atom;
import clojure.lang.IFn;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

import java.util.Arrays;


@Extension
public class VaultSecretsPlugin implements GoPlugin {

    public static final Logger LOGGER = Logger.getLoggerFor(VaultSecretsPlugin.class);

    private GoApplicationAccessor accessor;  // Set of JSON API's exposing GoCD info specifically curated for plugins.
    private IFn handler;  // Exposes plugin API
    private Atom client;  // The Vault Client


    /**
     * The plugin identifier tells GoCD what kind of plugin this is and what
     * version(s) of the request/response API it supports.
     */
    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return new GoPluginIdentifier("secrets", Arrays.asList("1.0"));
    }


    /**
     * Executed once at startup to inject an application accessor.
     */
    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor accessor) {
        LOGGER.info("Initializing plugin");
        this.accessor = accessor;

        IFn require = Clojure.var("clojure.core", "require");

        try {
            require.invoke(Clojure.read("amperity.gocd.secret.vault.plugin"));
            this.handler = Clojure.var("amperity.gocd.secret.vault.plugin", "handler");
        } catch (Exception ex) {
            LOGGER.error("Failed to load plugin API handler", ex);
            throw ex;
        }

        try {
            IFn init = Clojure.var("amperity.gocd.secret.vault.plugin", "initialize!");
            IFn logger = getLoggerFn();
            this.client = (Atom) init.invoke(logger, this.accessor);
        } catch (Exception ex) {
            LOGGER.error("Failed to initialize plugin state", ex);
            throw ex;
        }
    }


    /**
     * Handle a plugin request and return a response.
     *
     * The response is very much like a HTTP response — it has a status code, a
     * response body and optional headers.
     */
    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest request) throws UnhandledRequestTypeException {
        return (GoPluginApiResponse) this.handler.invoke(this.client, request);
    }


    /**
     * Internal method to proxy logger requests.
     *
     * NOTE: this is only here because the `go-plugin-api` package does not
     * actually include the `LoggingService` interface, which is only present
     * in the internal GoCD plugin API. This means any reference to the
     * `Logger` class from Clojure will introduce compilation errors with a
     * `ClassNotFoundException` pointing at the interface. ಠ_ಠ
     */
    private static void logPluginMessage(String level, String message, Throwable throwable) {
        switch (level) {
            case "debug":
                if (throwable != null) {
                    LOGGER.debug(message, throwable);
                } else {
                    LOGGER.debug(message);
                }
                break;

            case "info":
                if (throwable != null) {
                    LOGGER.info(message, throwable);
                } else {
                    LOGGER.info(message);
                }
                break;

            case "warn":
                if (throwable != null) {
                    LOGGER.warn(message, throwable);
                } else {
                    LOGGER.warn(message);
                }
                break;

            default:
                if (throwable != null) {
                    LOGGER.error(message, throwable);
                } else {
                    LOGGER.error(message);
                }
                break;
        }
    }

    /**
     * Constructs a new logging function to inject into the scheduler.
     */
    private IFn getLoggerFn() {
        return new clojure.lang.AFn() {
            public Object invoke(Object level, Object message, Object throwable) {
                logPluginMessage((String) level, (String) message, (Throwable) throwable);
                return null;
            }
        };
    }
}
