package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/**
 * Why a visible local-multiplayer game has no player count, as the Sources page counts and lists it (spec 014). The four
 * stored reasons come from the second source; {@code STEAM_NO_SOURCE} and {@code NOT_LOOKED_UP_YET} are derived from the
 * game itself (a Steam game no source has a count for; a console game the second source has not answered for yet, for
 * example because it is unconfigured or paused).
 */
public enum PlayerCountReason {
    STEAM_NO_SOURCE,
    ENTRIES_DISAGREE,
    NO_MATCH,
    COUNT_BELOW_TWO,
    CONSOLE_NOT_LISTED,
    NOT_LOOKED_UP_YET
}
