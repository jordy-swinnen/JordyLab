package dev.jordy.jordylab.gamecatalog.service;

/** The game is not on this console. */
public class GameNotOnConsoleException extends RuntimeException {

    public GameNotOnConsoleException(String message) {
        super(message);
    }

    public GameNotOnConsoleException() {
        super("The game is not on this console");
    }
}
