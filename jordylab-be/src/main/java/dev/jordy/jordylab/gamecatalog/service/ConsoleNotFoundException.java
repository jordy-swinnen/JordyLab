package dev.jordy.jordylab.gamecatalog.service;

/** The console does not exist. */
public class ConsoleNotFoundException extends RuntimeException {

    public ConsoleNotFoundException(String message) {
        super(message);
    }

    public ConsoleNotFoundException() {
        super("The console does not exist");
    }
}
