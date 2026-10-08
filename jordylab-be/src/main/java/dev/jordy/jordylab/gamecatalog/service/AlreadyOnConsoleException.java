package dev.jordy.jordylab.gamecatalog.service;

/** The game is already on this console. */
public class AlreadyOnConsoleException extends RuntimeException {

    public AlreadyOnConsoleException(String message) {
        super(message);
    }

    public AlreadyOnConsoleException() {
        super("The game is already on this console");
    }
}
