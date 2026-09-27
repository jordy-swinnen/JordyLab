package dev.jordy.jordylab.gamecatalog.domain;

/** Outcome of one owned or family library sync run. */
public enum LibrarySyncOutcome {
    APPLIED,
    NO_CHANGE,
    FAILED,
    SUSPICIOUS
}
