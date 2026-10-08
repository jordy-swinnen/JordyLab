package dev.jordy.jordylab.gamecatalog.service;

/** The game could not be found. */
public class ConsoleGameLookupException extends RuntimeException {

    public ConsoleGameLookupException(String message) {
        super(message);
    }

    public ConsoleGameLookupException() {
        super("The game could not be found");
    }
}
