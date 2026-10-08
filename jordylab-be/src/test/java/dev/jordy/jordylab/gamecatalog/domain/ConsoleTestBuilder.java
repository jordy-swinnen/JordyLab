package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
class ConsoleTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("c1a2b3d4-e5f6-4789-9abc-def012345678");
    public static final String DEFAULT_PLATFORM = "Nintendo Switch";

    public static Console aDefaultConsole() {
        return aConsole().build();
    }

    public static Console.ConsoleBuilder aConsole() {
        return Console.builder()
                .id(DEFAULT_ID)
                .platform(DEFAULT_PLATFORM);
    }
}
