package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsoleTest {

    @Test
    void buildConsoleDefaultsTheNameToThePlatform() {
        Console console = ConsoleTestBuilder.aDefaultConsole();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(console.getId()).isEqualTo(ConsoleTestBuilder.DEFAULT_ID);
            softly.assertThat(console.getPlatform()).isEqualTo("Nintendo Switch");
            softly.assertThat(console.getName()).isEqualTo("Nintendo Switch");
            softly.assertThat(console.label()).isEqualTo("Nintendo Switch");
            softly.assertThat(console.isCustomPlatform()).isFalse();
        });
    }

    @Test
    void buildCanonicalisesThePlatformSpelling() {
        Console console = ConsoleTestBuilder.aConsole().platform("PS5").name("Living room PS5").build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(console.getPlatform()).isEqualTo("PlayStation 5");
            softly.assertThat(console.getName()).isEqualTo("Living room PS5");
        });
    }

    @Test
    void buildWithACustomPlatformIsMarkedCustom() {
        Console console = ConsoleTestBuilder.aConsole().platform("My Arcade Cabinet").build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(console.getPlatform()).isEqualTo("My Arcade Cabinet");
            softly.assertThat(console.isCustomPlatform()).isTrue();
        });
    }

    @Test
    void theSamePlatformCanBeBuiltTwiceUnderDifferentNames() {
        Console first = ConsoleTestBuilder.aConsole().id(null).name("Kids' Switch").build();
        Console second = ConsoleTestBuilder.aConsole().id(null).name("Parents' Switch").build();

        assertThat(first.getPlatform()).isEqualTo(second.getPlatform());
        assertThat(first.getName()).isNotEqualTo(second.getName());
    }

    @Test
    void buildWithoutPlatform() {
        assertThatThrownBy(() -> ConsoleTestBuilder.aConsole().platform(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankPlatform() {
        assertThatThrownBy(() -> ConsoleTestBuilder.aConsole().platform(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithTooLongName() {
        assertThatThrownBy(() -> ConsoleTestBuilder.aConsole().name("x".repeat(41)).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renameTrimsTheName() {
        Console console = ConsoleTestBuilder.aDefaultConsole();

        console.rename("  Living room Switch ");

        assertThat(console.getName()).isEqualTo("Living room Switch");
    }

    @Test
    void renameRejectsBlankAndTooLongNames() {
        Console console = ConsoleTestBuilder.aDefaultConsole();

        assertThatThrownBy(() -> console.rename(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> console.rename("x".repeat(41))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(Console.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
