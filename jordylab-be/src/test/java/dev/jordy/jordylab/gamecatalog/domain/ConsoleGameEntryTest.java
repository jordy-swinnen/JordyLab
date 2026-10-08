package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsoleGameEntryTest {

    @Test
    void buildConsoleGameEntry() {
        ConsoleGameEntry entry = ConsoleGameEntryTestBuilder.aDefaultConsoleGameEntry();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(entry.getId()).isEqualTo(ConsoleGameEntryTestBuilder.DEFAULT_ID);
            softly.assertThat(entry.getGame()).isEqualTo(GameTestBuilder.aDefaultGame());
            softly.assertThat(entry.getConsole()).isEqualTo(ConsoleTestBuilder.aDefaultConsole());
        });
    }

    @Test
    void buildAssignsAnIdWhenNoneIsGiven() {
        assertThat(ConsoleGameEntryTestBuilder.aConsoleGameEntry().id(null).build().getId()).isNotNull();
    }

    @Test
    void buildWithoutGame() {
        assertThatThrownBy(() -> ConsoleGameEntryTestBuilder.aConsoleGameEntry().game(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutConsole() {
        assertThatThrownBy(() -> ConsoleGameEntryTestBuilder.aConsoleGameEntry().console(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(ConsoleGameEntry.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
