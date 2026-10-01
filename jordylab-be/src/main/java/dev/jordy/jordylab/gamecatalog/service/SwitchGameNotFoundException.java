package dev.jordy.jordylab.gamecatalog.service;

/**
 * The game, or its Switch installation, does not exist; the API answers {@code 404} (009 switch-api).
 */
public class SwitchGameNotFoundException extends IllegalArgumentException {

    public SwitchGameNotFoundException(String message) {
        super(message);
    }
}
