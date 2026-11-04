package dev.jordy.jordylab.identity.service;

/** A request carries a value the API cannot map (unknown status filter, unknown role). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
