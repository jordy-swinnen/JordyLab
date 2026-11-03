package dev.jordy.jordylab.identity.service;

/** The password violates the configured policy (spec 019 FR-002/FR-019). */
public class WeakPasswordException extends RuntimeException {

    public WeakPasswordException(String message) {
        super(message);
    }
}
