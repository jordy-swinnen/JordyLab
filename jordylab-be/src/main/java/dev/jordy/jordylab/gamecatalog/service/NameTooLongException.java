package dev.jordy.jordylab.gamecatalog.service;

/** A host display name or console name is longer than the 40 characters allowed. */
public class NameTooLongException extends RuntimeException {

    public NameTooLongException(int maxLength) {
        super("The name must not exceed " + maxLength + " characters");
    }
}
