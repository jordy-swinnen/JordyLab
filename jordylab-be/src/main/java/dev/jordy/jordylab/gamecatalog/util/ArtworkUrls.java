package dev.jordy.jordylab.gamecatalog.util;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtworkUrls {

    public static String externalCoverUrl(Game game) {
        return game.getCoverStatus() == ArtworkStatus.EXTERNAL_URL ? game.getCoverRef() : null;
    }

    public static String localCoverEndpoint(Game game) {
        return game.getCoverStatus() == ArtworkStatus.LOCAL_UPLOAD
                ? "/api/gamecatalog/games/" + game.getId() + "/artwork"
                : null;
    }
}
