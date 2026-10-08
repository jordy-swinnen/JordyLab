package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
class GameMarkTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("9d8c7b6a-5f4e-4d3c-b2a1-0f9e8d7c6b5a");
    public static final String DEFAULT_USER_SUBJECT = "6e0f4d8a-3b21-4c57-9e8f-a1b2c3d4e5f6";
    public static final MarkType DEFAULT_MARK = MarkType.WANT_TO_PLAY;

    public static GameMark aDefaultGameMark() {
        return aGameMark().build();
    }

    public static GameMark.GameMarkBuilder aGameMark() {
        return GameMark.builder()
                .id(DEFAULT_ID)
                .game(GameTestBuilder.aDefaultGame())
                .userSubject(DEFAULT_USER_SUBJECT)
                .mark(DEFAULT_MARK);
    }
}
