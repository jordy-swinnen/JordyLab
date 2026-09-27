package dev.jordy.jordylab.gamecatalog.service;

/**
 * Raised when the owned Steam library sync is requested but the account is not configured
 * ({@code STEAM_WEB_API_KEY} / {@code STEAM_ID} missing). This is a configuration state, not a
 * failure — no sync run is recorded and the catalog is untouched.
 */
public class SteamNotConfiguredException extends RuntimeException {

    public SteamNotConfiguredException(String message) {
        super(message);
    }
}
