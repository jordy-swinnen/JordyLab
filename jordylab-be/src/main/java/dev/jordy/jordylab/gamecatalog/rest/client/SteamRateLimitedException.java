package dev.jordy.jordylab.gamecatalog.rest.client;

/** Raised when Steam's store answers HTTP 429. A rate limit is not a game failure — the batch pauses. */
public class SteamRateLimitedException extends RuntimeException {

    public SteamRateLimitedException(String message) {
        super(message);
    }
}
