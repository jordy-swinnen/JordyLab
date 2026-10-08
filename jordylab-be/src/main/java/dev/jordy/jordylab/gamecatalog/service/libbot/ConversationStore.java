package dev.jordy.jordylab.gamecatalog.service.libbot;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import dev.jordy.jordylab.gamecatalog.LibBotProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * LibBot's memory for the length of a visit (spec 013 FR-011, FR-012, research A7): the last few exchanges of each
 * conversation, in this process only, never written anywhere. The key is the signed-in user's subject plus the
 * conversation id the browser chose, so one user can never address another user's conversation. Idle conversations
 * expire; a restart ends them.
 */
@Component
@RequiredArgsConstructor
public class ConversationStore {

    private static final int MAX_CONVERSATIONS = 2000;

    private final LibBotProperties properties;
    private final Ticker ticker;

    private Cache<String, List<ConversationTurn>> conversations;

    @PostConstruct
    void init() {
        this.conversations = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofHours(properties.memoryIdleHours()))
                .maximumSize(MAX_CONVERSATIONS)
                .ticker(ticker)
                .build();
    }

    /** The conversation so far, oldest first; empty for a new or expired one. */
    public List<ConversationTurn> history(String userSubject, String conversationId) {
        List<ConversationTurn> turns = conversations.getIfPresent(key(userSubject, conversationId));

        return turns == null ? List.of() : turns;
    }

    /** Adds a finished exchange and forgets the oldest ones beyond the memory length. */
    public void append(String userSubject, String conversationId, ConversationTurn turn) {
        conversations.asMap().compute(key(userSubject, conversationId), (key, existing) -> {
            List<ConversationTurn> turns = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            turns.add(turn);
            int excess = turns.size() - properties.memoryExchanges();

            return List.copyOf(excess > 0 ? turns.subList(excess, turns.size()) : turns);
        });
    }

    /** Idempotent: forgetting a conversation that is not there is fine. */
    public void clear(String userSubject, String conversationId) {
        conversations.invalidate(key(userSubject, conversationId));
    }

    private String key(String userSubject, String conversationId) {
        return userSubject + ":" + conversationId;
    }
}
