package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.InstallStatus;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.VoteTotalsResponse;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.UUID;

@UtilityClass
class GameSummaryResponseTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("77777777-0000-0000-0000-000000000007");
    public static final String DEFAULT_TITLE = "Mario Party";
    public static final String DEFAULT_PLATFORM = "Nintendo 64";
    public static final Boolean DEFAULT_LOCAL_MULTIPLAYER = Boolean.TRUE;

    public static GameSummaryResponse aDefaultGameSummaryResponse() {
        return aGameSummaryResponse(DEFAULT_ID, DEFAULT_TITLE, DEFAULT_LOCAL_MULTIPLAYER);
    }

    public static GameSummaryResponse aGameSummaryResponse(UUID id, String title, Boolean localMultiplayer) {
        return new GameSummaryResponse(
                id,
                title,
                List.of(new PlatformChip(DEFAULT_PLATFORM, null, null, null, null)),
                List.of(GameSource.EMULATED),
                null,
                null,
                null,
                InstallStatus.INSTALLED,
                localMultiplayer,
                new VoteTotalsResponse(3, 2, 1),
                MarkType.PLAYED_LIKED,
                null);
    }
}
