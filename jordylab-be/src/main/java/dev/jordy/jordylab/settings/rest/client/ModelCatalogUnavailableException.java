package dev.jordy.jordylab.settings.rest.client;

/** The gateway's model list could not be fetched and nothing is cached yet. */
public class ModelCatalogUnavailableException extends RuntimeException {

    public ModelCatalogUnavailableException(Throwable cause) {
        super("Gateway model catalog unavailable", cause);
    }
}
