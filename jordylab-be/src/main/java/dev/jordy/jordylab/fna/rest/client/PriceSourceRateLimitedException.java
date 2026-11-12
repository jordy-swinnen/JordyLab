package dev.jordy.jordylab.fna.rest.client;

/** Yahoo answered HTTP 429 to a history request: the refresh cycle stops and the next cycle retries (spec 023, research section 4). */
public class PriceSourceRateLimitedException extends RuntimeException {

    public PriceSourceRateLimitedException(String priceSymbol, Throwable cause) {
        super("Price source rate-limited the history request for " + priceSymbol, cause);
    }
}
