package dev.jordy.jordylab.settings.service;

/** Thrown when a request names an app key that is not in {@link ManagedApp}. */
public class AppNotFoundException extends RuntimeException {

    public AppNotFoundException(String appKey) {
        super("Unknown app: " + appKey);
    }
}
