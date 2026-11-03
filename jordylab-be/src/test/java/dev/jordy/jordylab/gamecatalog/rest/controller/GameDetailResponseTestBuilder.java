package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.DescriptionSource;
import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.InstallStatus;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.DescriptionResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlaceResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.VoteTotalsResponse;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.UUID;

@UtilityClass
class GameDetailResponseTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("77777777-0000-0000-0000-000000000007");
    public static final String DEFAULT_TITLE = "Mario Party";
    public static final String DEFAULT_PLATFORM = "Nintendo 64";
    public static final String DEFAULT_HOST_LABEL = "Living room PC";

    public static GameDetailResponse aDefaultGameDetailResponse() {
        return new GameDetailResponse(
                DEFAULT_ID,
                DEFAULT_TITLE,
                List.of(new PlatformChip(DEFAULT_PLATFORM, null, null, null, null)),
                List.of(GameSource.EMULATED),
                List.of(new PlaceResponse(PlaceResponse.PlaceKind.HOST_COPY, null, null, null, DEFAULT_HOST_LABEL,
                        DEFAULT_PLATFORM, Boolean.TRUE, RomStatus.VALIDATED, null, null)),
                null, null, null, null, null, null, null, null,
                "Party",
                "Nintendo",
                "Nintendo",
                1998,
                null,
                4,
                null,
                Boolean.FALSE,
                Boolean.FALSE,
                new DescriptionResponse("A board game party for up to four players.", DescriptionSource.AI, null, null, null),
                null,
                InstallStatus.INSTALLED,
                Boolean.TRUE,
                Boolean.TRUE,
                Boolean.FALSE,
                null,
                null,
                new VoteTotalsResponse(3, 2, 1),
                MarkType.PLAYED_LIKED);
    }
}
