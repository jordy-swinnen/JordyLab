package dev.jordy.jordylab.gamecatalog.domain;

/** Why the second player-count source left a console game without a count. */
public enum PlayerCountGap {
    NO_MATCH,
    ENTRIES_DISAGREE,
    COUNT_BELOW_TWO,
    CONSOLE_NOT_LISTED
}
