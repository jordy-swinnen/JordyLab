package dev.jordy.jordylab.gamecatalog.domain;

/**
 * Whether the ROM of one emulated copy launches (spec 013 US13): {@code VALIDATED} = tested and works, {@code BROKEN} = the
 * ROM is present but the game does not start. Only emulated copies carry a status (FR-051).
 */
public enum RomStatus {
    UNKNOWN,
    VALIDATED,
    BROKEN
}
