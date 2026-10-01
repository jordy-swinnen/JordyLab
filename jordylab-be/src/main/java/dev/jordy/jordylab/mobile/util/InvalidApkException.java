package dev.jordy.jordylab.mobile.util;

/**
 * An upload to {@code POST /api/mobile/releases} is not a readable, signed APK, so its signing certificate
 * can't be checked (spec 007 FR-001/FR-003, contracts/mobile-releases-api.md → {@code 400 INVALID_APK}).
 */
public class InvalidApkException extends IllegalArgumentException {

    public InvalidApkException(String message) {
        super(message);
    }

    public InvalidApkException(String message, Throwable cause) {
        super(message, cause);
    }
}
