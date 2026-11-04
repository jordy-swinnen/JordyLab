package dev.jordy.jordylab.identity.service;

import java.util.UUID;

public class AlreadyApprovedException extends RuntimeException {

    public AlreadyApprovedException(UUID userId) {
        super("User is already approved: " + userId);
    }
}
