package dev.jordy.jordylab.gamecatalog.service.libbot;

import com.github.benmanes.caffeine.cache.Ticker;
import dev.jordy.jordylab.gamecatalog.LibBotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ConversationStoreTest {

    private static final String CONVERSATION = "7b1f6c2e";

    private final AtomicLong nanos = new AtomicLong();
    private ConversationStore store;

    @BeforeEach
    void setUp() {
        Ticker ticker = nanos::get;
        store = new ConversationStore(new LibBotProperties(10, 2, 10, 15, 5, 1000), ticker);
        store.init();
    }

    private static ConversationTurn turn(int number) {
        return new ConversationTurn("question " + number, "answer " + number, List.of(), LibBotOutcome.ANSWERED);
    }

    @Test
    void keepsTheLastTenExchangesOldestFirst() {
        for (int number = 1; number <= 12; number++) {
            store.append("alice", CONVERSATION, turn(number));
        }

        List<ConversationTurn> history = store.history("alice", CONVERSATION);

        assertSoftly(softly -> {
            softly.assertThat(history).hasSize(10);
            softly.assertThat(history.get(0).userText()).isEqualTo("question 3");
            softly.assertThat(history.get(9).userText()).isEqualTo("question 12");
        });
    }

    @Test
    void anotherUserCannotReadOrClearAConversationWithTheSameId() {
        store.append("alice", CONVERSATION, turn(1));

        store.clear("bob", CONVERSATION);

        assertSoftly(softly -> {
            softly.assertThat(store.history("bob", CONVERSATION)).isEmpty();
            softly.assertThat(store.history("alice", CONVERSATION)).hasSize(1);
        });
    }

    @Test
    void clearingForgetsTheConversationAndIsIdempotent() {
        store.append("alice", CONVERSATION, turn(1));

        store.clear("alice", CONVERSATION);
        store.clear("alice", CONVERSATION);

        assertThat(store.history("alice", CONVERSATION)).isEmpty();
    }

    @Test
    void anIdleConversationExpiresButAnActiveOneDoesNot() {
        store.append("alice", CONVERSATION, turn(1));
        nanos.addAndGet(Duration.ofMinutes(100).toNanos());
        assertThat(store.history("alice", CONVERSATION)).hasSize(1);

        nanos.addAndGet(Duration.ofMinutes(100).toNanos());
        assertThat(store.history("alice", CONVERSATION)).hasSize(1);

        nanos.addAndGet(Duration.ofHours(3).toNanos());
        assertThat(store.history("alice", CONVERSATION)).isEmpty();
    }

    @Test
    void conversationsAreIndependent() {
        store.append("alice", "one", turn(1));
        store.append("alice", "two", turn(2));

        assertSoftly(softly -> {
            softly.assertThat(store.history("alice", "one")).extracting(ConversationTurn::userText)
                    .containsExactly("question 1");
            softly.assertThat(store.history("alice", "two")).extracting(ConversationTurn::userText)
                    .containsExactly("question 2");
        });
    }
}
