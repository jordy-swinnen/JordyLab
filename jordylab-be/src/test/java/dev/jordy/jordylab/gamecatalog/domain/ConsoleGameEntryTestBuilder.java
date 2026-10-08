package dev.jordy.jordylab.gamecatalog.domain;

import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
class ConsoleGameEntryTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("e7f80910-2a3b-4c5d-8e6f-7a8b9c0d1e2f");

    public static ConsoleGameEntry aDefaultConsoleGameEntry() {
        return aConsoleGameEntry().build();
    }

    public static ConsoleGameEntry.ConsoleGameEntryBuilder aConsoleGameEntry() {
        return ConsoleGameEntry.builder()
                .id(DEFAULT_ID)
                .game(GameTestBuilder.aDefaultGame())
                .console(ConsoleTestBuilder.aDefaultConsole());
    }
}
