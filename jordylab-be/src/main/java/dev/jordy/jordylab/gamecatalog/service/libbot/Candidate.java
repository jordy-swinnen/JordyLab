package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

/**
 * A game LibBot may talk about. {@code confirmed} means every required fact is known and satisfied; an unconfirmed
 * candidate has at least one required fact still unknown. Votes are the public community totals; {@code similarity} is
 * the cosine similarity to the question's taste text (null when there was none or no vector search was possible).
 * {@code myMark} is the asker's own mark on the game, if any (never anyone else's).
 */
@Builder(toBuilder = true)
public record Candidate(
        UUID gameId,
        String title,
        List<String> platforms,
        boolean confirmed,
        int wantVotes,
        int likedVotes,
        int dislikedVotes,
        Double similarity,
        String genres,
        String developer,
        Integer releaseYear,
        Integer maxLocalPlayers,
        Boolean localMultiplayer,
        Boolean singlePlayer,
        Boolean onlineMultiplayer,
        String description,
        MarkType myMark,
        List<RomCopy> romCopies) {

    /** One emulated copy: the machine that holds it and whether its ROM launches there. */
    public record RomCopy(String machine, RomStatus status) {
    }

    public Candidate {
        romCopies = romCopies == null ? List.of() : List.copyOf(romCopies);
    }

    /** True when at least one machine has a ROM that is known to launch. */
    public boolean hasValidatedCopy() {
        return romCopies.stream().anyMatch(copy -> copy.status() == RomStatus.VALIDATED);
    }

    /** Net community weight: wanted plus liked, minus disliked. */
    public int voteScore() {
        return wantVotes + likedVotes - dislikedVotes;
    }
}
