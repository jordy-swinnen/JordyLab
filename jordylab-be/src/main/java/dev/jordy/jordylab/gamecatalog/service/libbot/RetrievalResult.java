package dev.jordy.jordylab.gamecatalog.service.libbot;

import java.util.List;

/**
 * What the retriever found: at most a handful of confirmed candidates (best first), and the games whose required facts are
 * still unknown, as a count plus a few titles. {@code unknownCount} is the total; {@code unknownFlagCount} is the subset
 * whose local-multiplayer flag itself is unknown, as opposed to a game known to be local multiplayer without a player
 * count. Unknown games are never mixed into the confirmed list (spec 013 FR-004).
 */
public record RetrievalResult(List<Candidate> confirmed, int unknownCount, int unknownFlagCount,
        List<Candidate> unknownSamples, boolean semanticUsed) {

    public RetrievalResult {
        confirmed = confirmed == null ? List.of() : List.copyOf(confirmed);
        unknownSamples = unknownSamples == null ? List.of() : List.copyOf(unknownSamples);
        unknownFlagCount = Math.max(0, Math.min(unknownFlagCount, unknownCount));
    }

    public boolean isEmpty() {
        return confirmed.isEmpty() && unknownCount == 0;
    }
}
