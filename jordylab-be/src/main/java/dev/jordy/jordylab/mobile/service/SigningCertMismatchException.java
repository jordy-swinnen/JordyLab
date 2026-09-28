package dev.jordy.jordylab.mobile.service;

/** Spec FR-003: an uploaded APK's signing certificate does not match the configured one. */
public class SigningCertMismatchException extends RuntimeException {

    public SigningCertMismatchException() {
        super("Uploaded APK's signing-certificate SHA-256 does not match jordylab.mobile.release.signing-cert-sha256");
    }
}
