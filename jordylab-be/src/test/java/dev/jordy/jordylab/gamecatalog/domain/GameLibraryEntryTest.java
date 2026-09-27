package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameLibraryEntryTest {

    private static final Instant REMOVED_AT = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant SEEN_AT = Instant.parse("2026-03-01T00:00:00Z");

    @Test
    void buildGameLibraryEntry() {
        GameLibraryEntry entry = GameLibraryEntryTestBuilder.aDefaultGameLibraryEntry();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(entry.getId()).isNotNull();
            softly.assertThat(entry.getLibrarySource()).isEqualTo(LibrarySource.OWNED);
            softly.assertThat(entry.getFirstSeenAt()).isEqualTo(GameLibraryEntryTestBuilder.DEFAULT_FIRST_SEEN);
            softly.assertThat(entry.getLastSeenAt()).isEqualTo(GameLibraryEntryTestBuilder.DEFAULT_LAST_SEEN);
            softly.assertThat(entry.isActive()).isTrue();
            softly.assertThat(entry.getRemovedAt()).isNull();
        });
    }

    @Test
    void buildWithoutGame() {
        assertThatThrownBy(() -> GameLibraryEntryTestBuilder.aGameLibraryEntry().game(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutLibrarySource() {
        assertThatThrownBy(() -> GameLibraryEntryTestBuilder.aGameLibraryEntry().librarySource(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithDerivedLocalSourceIsRejected() {
        assertThatThrownBy(() -> GameLibraryEntryTestBuilder.aGameLibraryEntry()
                .librarySource(LibrarySource.LOCAL).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutTimestamps() {
        assertThatThrownBy(() -> GameLibraryEntryTestBuilder.aGameLibraryEntry().firstSeenAt(null).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GameLibraryEntryTestBuilder.aGameLibraryEntry().lastSeenAt(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seenAgainClearsRemovalAndUpdatesLastSeen() {
        GameLibraryEntry entry = GameLibraryEntryTestBuilder.aDefaultGameLibraryEntry();
        entry.markRemoved(REMOVED_AT);

        entry.seenAgain(SEEN_AT);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(entry.getRemovedAt()).isNull();
            softly.assertThat(entry.getLastSeenAt()).isEqualTo(SEEN_AT);
            softly.assertThat(entry.isActive()).isTrue();
        });
    }

    @Test
    void markRemovedMakesTheEntryInactive() {
        GameLibraryEntry entry = GameLibraryEntryTestBuilder.aDefaultGameLibraryEntry();

        entry.markRemoved(REMOVED_AT);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(entry.isActive()).isFalse();
            softly.assertThat(entry.getRemovedAt()).isEqualTo(REMOVED_AT);
        });
    }

    @Test
    void isWithinGraceComparesAgainstTheCutoff() {
        GameLibraryEntry entry = GameLibraryEntryTestBuilder.aDefaultGameLibraryEntry();
        entry.markRemoved(REMOVED_AT);

        assertThat(entry.isWithinGrace(REMOVED_AT.minusSeconds(1))).isTrue();
        assertThat(entry.isWithinGrace(REMOVED_AT.plusSeconds(1))).isFalse();
        assertThat(entry.isWithinGrace(REMOVED_AT)).isTrue();
    }

    @Test
    void activeEntryIsNeverWithinGrace() {
        GameLibraryEntry entry = GameLibraryEntryTestBuilder.aDefaultGameLibraryEntry();

        assertThat(entry.isWithinGrace(Instant.now())).isFalse();
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(GameLibraryEntry.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
