package dev.jordy.jordylab.settings.rest.client;

/**
 * Raised whenever the Keycloak Admin REST API or its token endpoint cannot be reached or
 * fails — surfaced by the controller as {@code 503 KEYCLOAK_UNAVAILABLE} (constitution
 * principle II: no silent partial state).
 */
public class KeycloakUnavailableException extends RuntimeException {

    public KeycloakUnavailableException(String message) {
        super(message);
    }

    public KeycloakUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
