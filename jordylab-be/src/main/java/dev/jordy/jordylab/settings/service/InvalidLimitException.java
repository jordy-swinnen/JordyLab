package dev.jordy.jordylab.settings.service;

/** Thrown when a chat limit is not a whole number of 0 or more (spec 014 FR-011). */
public class InvalidLimitException extends RuntimeException {

    public InvalidLimitException(int dailyLimit) {
        super("Invalid daily limit: " + dailyLimit);
    }
}
