package dev.jordy.jordylab.identity.service;

/** The chosen username already has an account (spec 019 US1). */
public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String username) {
        super("Username already taken: " + username);
    }
}
