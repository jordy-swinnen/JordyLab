package dev.jordy.jordylab.mobile.util;

/** Spec FR-003: an uploaded file is not a readable, signed APK — a client error, never a 500/403. */
public class InvalidApkException extends IllegalArgumentException {

    public InvalidApkException(String message) {
        super(message);
    }

    public InvalidApkException(String message, Throwable cause) {
        super(message, cause);
    }
}
