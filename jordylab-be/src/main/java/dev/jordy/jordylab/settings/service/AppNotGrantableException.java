package dev.jordy.jordylab.settings.service;

/**
 * Thrown when a grant is requested for an app that may never be granted to anyone — FNA (it holds the admin's own
 * data) and Settings (spec 014 FR-005, FR-006). The server refuses even when the panel is bypassed.
 */
public class AppNotGrantableException extends RuntimeException {

    public AppNotGrantableException(String appKey) {
        super("App cannot be granted: " + appKey);
    }
}
