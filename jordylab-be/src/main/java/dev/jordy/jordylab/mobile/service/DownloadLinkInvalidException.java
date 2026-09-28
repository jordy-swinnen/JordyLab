package dev.jordy.jordylab.mobile.service;

/** Spec FR-002: an expired, tampered, or unknown download token (research D8). */
public class DownloadLinkInvalidException extends RuntimeException {

    public DownloadLinkInvalidException(String message) {
        super(message);
    }
}
