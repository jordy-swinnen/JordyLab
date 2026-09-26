package dev.jordy.jordylab.gamecatalog.domain;

public enum SyncOutcome {
    APPLIED,
    NO_CHANGE,
    OUT_OF_ORDER,
    SCAN_FAILED,
    REJECTED
}
