package dev.jordy.jordylab.identity.service;

/** The username violates the allowed shape (lowercase letters, digits, dot, dash, underscore). */
public class InvalidUsernameException extends RuntimeException {

    public InvalidUsernameException(String message) {
        super(message);
    }
}
