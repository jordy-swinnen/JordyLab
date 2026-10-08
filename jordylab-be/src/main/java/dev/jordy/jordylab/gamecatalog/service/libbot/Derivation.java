package dev.jordy.jordylab.gamecatalog.service.libbot;

import java.util.List;

/**
 * The result of {@link ConstraintDeriver}: the constraints to search with, the chips to show, and whether the party is
 * bigger than any group a game is known to support (then nothing can be confirmed, only unknown games remain).
 */
public record Derivation(Constraints constraints, List<AppliedConstraint> applied, boolean partyExceedsKnownGroups,
        Integer largestKnownGroup) {
}
