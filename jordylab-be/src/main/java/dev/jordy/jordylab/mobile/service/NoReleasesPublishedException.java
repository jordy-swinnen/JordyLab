package dev.jordy.jordylab.mobile.service;

/** contracts/mobile-releases-api.md: {@code GET /latest} before any release has been published. */
public class NoReleasesPublishedException extends RuntimeException {

    public NoReleasesPublishedException() {
        super("No mobile releases have been published yet");
    }
}
