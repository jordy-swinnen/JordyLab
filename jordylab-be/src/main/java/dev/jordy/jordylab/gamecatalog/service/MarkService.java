package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameMarkRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.MarkResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.VoteTotalsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Each person holds at most one of the three marks per game (want to play, played and liked, played and disliked).
 * Choosing another replaces it, choosing none clears it, and the totals are public while the voters never are
 * (spec 013 FR-043 to FR-046). The voter is always the authenticated subject, never something a request says.
 */
@Service
@RequiredArgsConstructor
public class MarkService {

    private final GameRepository gameRepository;
    private final GameMarkRepository gameMarkRepository;

    /** Sets, replaces or (null) clears {@code userSubject}'s mark; empty when the game is not visible. */
    @Transactional
    public Optional<MarkResponse> setMark(UUID gameId, String userSubject, MarkType mark) {
        if (gameRepository.findVisibleById(gameId).isEmpty()) {
            return Optional.empty();
        }
        if (mark == null) {
            gameMarkRepository.deleteByGameIdAndUserSubject(gameId, userSubject);
        } else {
            gameMarkRepository.upsert(UUID.randomUUID(), gameId, userSubject, mark.name());
        }

        return Optional.of(new MarkResponse(totalsOf(gameId), mark));
    }

    @Transactional(readOnly = true)
    public VoteTotalsResponse totalsOf(UUID gameId) {
        long[] counts = new long[MarkType.values().length];
        for (GameMarkRepository.VoteTotal total : gameMarkRepository.countVotes(List.of(gameId))) {
            counts[total.getMark().ordinal()] = total.getTotal();
        }

        return new VoteTotalsResponse(counts[MarkType.WANT_TO_PLAY.ordinal()], counts[MarkType.PLAYED_LIKED.ordinal()],
                counts[MarkType.PLAYED_DISLIKED.ordinal()]);
    }
}
