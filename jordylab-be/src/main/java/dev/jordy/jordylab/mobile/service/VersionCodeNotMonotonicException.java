package dev.jordy.jordylab.mobile.service;

/** Data-model.md invariant: a publish call's versionCode must exceed the current latest. */
public class VersionCodeNotMonotonicException extends RuntimeException {

    public VersionCodeNotMonotonicException(int versionCode, int currentLatest) {
        super("versionCode " + versionCode + " must be greater than the current latest (" + currentLatest + ")");
    }
}
