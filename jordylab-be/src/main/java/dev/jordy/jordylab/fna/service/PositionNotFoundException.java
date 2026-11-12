package dev.jordy.jordylab.fna.service;

import java.util.UUID;

/** The id matches no tracked position (held or wishlisted), so there is no price history to show (spec 023, FR-001). */
public class PositionNotFoundException extends RuntimeException {

    public PositionNotFoundException(UUID positionId) {
        super("No tracked position with id " + positionId);
    }
}
