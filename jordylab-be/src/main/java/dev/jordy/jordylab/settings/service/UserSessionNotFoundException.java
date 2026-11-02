package dev.jordy.jordylab.settings.service;

/** Thrown when a session to end does not belong to that person (spec 014 FR-019). */
public class UserSessionNotFoundException extends RuntimeException {

    public UserSessionNotFoundException(String sessionId) {
        super("Session not found: " + sessionId);
    }
}
