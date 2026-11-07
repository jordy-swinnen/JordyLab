package dev.jordy.jordylab.fna.service;

import java.util.UUID;

/** The id is unknown or belongs to a wishlist item, which has no price history view (spec 023, FR-001). */
public class PositionNotHeldException extends RuntimeException {

    public PositionNotHeldException(UUID positionId) {
        super("No held position with id " + positionId);
    }
}
