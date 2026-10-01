package dev.jordy.jordylab.gamecatalog.service;

/**
 * The game is already tracked on the Switch; the API answers {@code 409} (009 switch-api) and the bulk add reports
 * the line as already present. Other {@link IllegalStateException}s (e.g. a missing virtual Switch source) are server
 * misconfigurations and stay {@code 500}.
 */
public class SwitchGameAlreadyPresentException extends IllegalStateException {

    public SwitchGameAlreadyPresentException(String message) {
        super(message);
    }
}
