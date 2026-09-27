package dev.jordy.jordylab.settings.service;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("No such user: " + userId);
    }
}
