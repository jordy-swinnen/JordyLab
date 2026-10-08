package dev.jordy.jordylab.gamecatalog.service.libbot;

import java.util.List;
import java.util.UUID;

/**
 * One finished exchange kept for the length of a visit (spec 013 FR-011): what was asked, what LibBot answered, and the
 * games it named, so "those" and "the first one" resolve to exact games. Never stored beyond memory.
 */
public record ConversationTurn(String userText, String answerText, List<CitedGame> cited, LibBotOutcome outcome) {

    public ConversationTurn {
        cited = cited == null ? List.of() : List.copyOf(cited);
    }

    public record CitedGame(UUID gameId, String title) {
    }
}
