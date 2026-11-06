package dev.jordy.jordylab.gamecatalog.service.libbot;

import java.util.List;

/**
 * The result of {@link ConstraintDeriver}: the constraints to search with, the chips to show, and whether the party is
 * bigger than any group a game is known to support (then nothing can be confirmed, only unknown games remain).
 * {@code impossible} is true when the stated facts contradict each other ("single player games for eight people"), so
 * the answer is a no-match without a search or a model call.
 */
public record Derivation(Constraints constraints, List<AppliedConstraint> applied, boolean partyExceedsKnownGroups,
        Integer largestKnownGroup, boolean impossible) {
}
