package dev.jordy.jordylab.feedback.service;

/**
 * No ticket exists for the given id on an admin endpoint (contracts/rest-api.md).
 */
public class TicketNotFoundException extends RuntimeException {

    public TicketNotFoundException(String message) {
        super(message);
    }
}
