package dev.jordy.jordylab.mobile.service;

import java.util.UUID;

public class ReleaseNotFoundException extends RuntimeException {

    public ReleaseNotFoundException(UUID releaseId) {
        super("MobileRelease not found: " + releaseId);
    }
}
