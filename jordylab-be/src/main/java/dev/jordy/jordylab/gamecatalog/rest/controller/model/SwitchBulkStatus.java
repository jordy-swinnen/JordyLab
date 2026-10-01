package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/**
 * Review status of one pasted line in a Switch bulk add (spec 009 US3, data-model {@code BulkReviewLine}).
 */
public enum SwitchBulkStatus {
    /** IGDB's best match has the same title as the line. */
    MATCH,
    /** IGDB found nothing; the line can still be added manually by title. */
    NO_MATCH,
    /** The game is already tracked on the Switch. */
    ALREADY_PRESENT,
    /** A best match exists but its title differs, or the line looks like DLC or a bundle. */
    NEEDS_REVIEW
}
