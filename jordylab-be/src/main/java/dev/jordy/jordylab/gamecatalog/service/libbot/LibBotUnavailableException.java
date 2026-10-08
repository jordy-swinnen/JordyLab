package dev.jordy.jordylab.gamecatalog.service.libbot;

/** LibBot could not produce an answer (providers failed or answered unusably). Does not cost a guest message. */
public class LibBotUnavailableException extends RuntimeException {

    public LibBotUnavailableException(String message) {
        super(message);
    }

    public LibBotUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
