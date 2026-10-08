package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
class GameTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    public static final String DEFAULT_TITLE = "Super Mario World";

    public static Game aDefaultGame() {
        return aGame().build();
    }

    public static Game.GameBuilder aGame() {
        return Game.builder()
                .id(DEFAULT_ID)
                .title(DEFAULT_TITLE);
    }
}
