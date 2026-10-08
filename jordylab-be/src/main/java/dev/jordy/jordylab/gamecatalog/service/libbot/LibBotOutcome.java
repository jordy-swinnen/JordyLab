package dev.jordy.jordylab.gamecatalog.service.libbot;

/** How a LibBot question ended (spec 013 contracts/libbot-api.md). Only {@code ANSWERED} carries references. */
public enum LibBotOutcome {
    ANSWERED,
    NO_MATCH,
    CLARIFY,
    OUT_OF_SCOPE,
    EMPTY_LIBRARY
}
