package dev.jordy.jordylab.identity.domain;

/**
 * The scope a machine token carries (spec 019 D8). After spec 018 removed the APK stack the scanner is the only
 * non-interactive caller, so {@code INGEST} is deliberately the single value.
 */
public enum MachineTokenScope {

    INGEST
}
