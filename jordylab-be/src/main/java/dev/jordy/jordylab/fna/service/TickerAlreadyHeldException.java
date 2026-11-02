package dev.jordy.jordylab.fna.service;

/**
 * Raised when a ticker is added to the wishlist while it is already a held portfolio position (spec 015 FR-003): a
 * ticker is held or wishlisted, never both.
 */
public class TickerAlreadyHeldException extends RuntimeException {

    public TickerAlreadyHeldException(String ticker) {
        super("Ticker " + ticker + " is already held in the portfolio");
    }
}
