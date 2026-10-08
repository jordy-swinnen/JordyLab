package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameMarkTest {

    @Test
    void buildGameMark() {
        GameMark mark = GameMarkTestBuilder.aDefaultGameMark();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mark.getId()).isEqualTo(GameMarkTestBuilder.DEFAULT_ID);
            softly.assertThat(mark.getGame()).isEqualTo(GameTestBuilder.aDefaultGame());
            softly.assertThat(mark.getUserSubject()).isEqualTo(GameMarkTestBuilder.DEFAULT_USER_SUBJECT);
            softly.assertThat(mark.getMark()).isEqualTo(MarkType.WANT_TO_PLAY);
        });
    }

    @Test
    void buildAssignsAnIdWhenNoneIsGiven() {
        assertThat(GameMarkTestBuilder.aGameMark().id(null).build().getId()).isNotNull();
    }

    @Test
    void buildWithoutGame() {
        assertThatThrownBy(() -> GameMarkTestBuilder.aGameMark().game(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutUserSubject() {
        assertThatThrownBy(() -> GameMarkTestBuilder.aGameMark().userSubject(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankUserSubject() {
        assertThatThrownBy(() -> GameMarkTestBuilder.aGameMark().userSubject(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutMark() {
        assertThatThrownBy(() -> GameMarkTestBuilder.aGameMark().mark(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeToReplacesTheMark() {
        GameMark mark = GameMarkTestBuilder.aDefaultGameMark();

        mark.changeTo(MarkType.PLAYED_DISLIKED);

        assertThat(mark.getMark()).isEqualTo(MarkType.PLAYED_DISLIKED);
    }

    @Test
    void changeToRejectsNull() {
        GameMark mark = GameMarkTestBuilder.aDefaultGameMark();

        assertThatThrownBy(() -> mark.changeTo(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(GameMark.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
