package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Console;
import dev.jordy.jordylab.gamecatalog.domain.ConsoleGameEntry;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.GameMark;
import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.Presence;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * One game, many places (spec 013 US5, FR-023 to FR-026): the same title on Steam, an emulator host and a console is one
 * game; adding a place never touches richer data; removing a place keeps the game while another remains; the last place
 * going (after the grace period) takes the game with its marks and search-index row.
 */
class MultiPlacePlayScenariosTest extends ModuleScenarioSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Console aConsole() {
        return consoleRepository.save(Console.builder().platform("Nintendo Switch").name("Switch dock").build());
    }

    @Test
    void theSameTitleOnSteamAnEmulatorHostAndAConsoleIsOneGameWithThreePlaces() {
        scanService.submitScan(emuDeckScan("htpc", "Hades"));
        scanService.submitScan(steamScan("desktop", "1145360", "Hades"));
        Game onConsole = gameIdentityService.resolveOrCreateByTitle("Hades", TitleSource.MANIFEST);
        consoleGameEntryRepository.save(ConsoleGameEntry.builder().game(onConsole).console(aConsole()).build());

        assertThat(gameRepository.count()).isEqualTo(1);
        GameDetailResponse detail = gameQueryService.getGameDetail(onlyGame().getId(), null).orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(detail.places()).hasSize(3);
            softly.assertThat(detail.platforms()).extracting(PlatformChip::name)
                    .containsExactlyInAnyOrder("SNES", "Steam", "Nintendo Switch");
            softly.assertThat(detail.sources()).containsExactlyInAnyOrder(GameSource.STEAM_OWNED, GameSource.EMULATED,
                    GameSource.CONSOLE);
            softly.assertThat(onlyGame().getSteamAppId()).isEqualTo("1145360");
        });
    }

    @Test
    void aSecondHostsScanAddsAPlaceWithoutTouchingEnrichmentArtworkOrMarks() {
        scanService.submitScan(emuDeckScan("htpc", "Super Mario World"));
        Game game = onlyGame();
        game.applyDeterministicDescription("A classic platformer.");
        game.applyDeterministicMetadata("Platformer", "Nintendo", "Nintendo", 1990);
        game.applyCoverArtwork(dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus.EXTERNAL_URL, "https://x/c.jpg");
        gameRepository.save(game);
        gameMarkRepository.save(GameMark.builder().game(game).userSubject("alice").mark(MarkType.PLAYED_LIKED).build());

        scanService.submitScan(emuDeckScan("laptop", "Super Mario World"));

        Game after = onlyGame();
        assertSoftly(softly -> {
            softly.assertThat(gameRepository.count()).isEqualTo(1);
            softly.assertThat(gameInstallationRepository.count()).isEqualTo(2);
            softly.assertThat(after.getDescription()).isEqualTo("A classic platformer.");
            softly.assertThat(after.getGenres()).isEqualTo("Platformer");
            softly.assertThat(after.getCoverRef()).isEqualTo("https://x/c.jpg");
            softly.assertThat(gameMarkRepository.count()).isEqualTo(1);
        });
    }

    @Test
    void removingOneCopyKeepsTheGameWhileAnotherPlaceRemains() {
        scanService.submitScan(emuDeckScan("htpc", "Hades"));
        scanService.submitScan(steamScan("desktop", "1145360", "Hades"));

        scanService.submitScan(emuDeckScan("htpc", true));

        Game game = onlyGame();
        GameDetailResponse detail = gameQueryService.getGameDetail(game.getId(), null).orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(gameRepository.count()).isEqualTo(1);
            softly.assertThat(detail.places()).hasSize(1);
            softly.assertThat(detail.platforms()).extracting(PlatformChip::name).containsExactly("Steam");
        });
    }

    @Test
    void theLastPlaceGoingAfterTheGracePeriodTakesTheGameItsMarksAndItsSearchIndexRow() {
        scanService.submitScan(emuDeckScan("htpc", "Hades"));
        Game game = onlyGame();
        gameMarkRepository.save(GameMark.builder().game(game).userSubject("alice").mark(MarkType.WANT_TO_PLAY).build());
        transactions.executeWithoutResult(status -> gameEmbeddingRepository.upsert(game.getId(), "m", "h",
                GameEmbeddingService.vectorLiteral(new float[1536]), Instant.now()));
        scanService.submitScan(emuDeckScan("htpc", true));
        GameInstallation gone = gameInstallationRepository.findAll().getFirst();
        assertThat(gone.getPresence()).isEqualTo(Presence.UNINSTALLED);
        assertThat(gameRepository.count()).as("kept during the grace period").isEqualTo(1);

        jdbcTemplate.update("UPDATE gamecatalog.game_installation SET uninstalled_at = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(40L * 24 * 3600)));
        reconciliationService.purgeUninstalledGames();

        assertSoftly(softly -> {
            softly.assertThat(gameRepository.count()).isZero();
            softly.assertThat(gameMarkRepository.count()).isZero();
            softly.assertThat(gameEmbeddingRepository.count()).isZero();
        });
    }

    @Test
    void aTitleOnAConsoleKeepsTheGameAliveWhenTheScannedCopyIsPurged() {
        scanService.submitScan(emuDeckScan("htpc", "Hades"));
        Game game = onlyGame();
        consoleGameEntryRepository.save(ConsoleGameEntry.builder().game(game).console(aConsole()).build());
        scanService.submitScan(emuDeckScan("htpc", true));
        jdbcTemplate.update("UPDATE gamecatalog.game_installation SET uninstalled_at = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(40L * 24 * 3600)));

        reconciliationService.purgeUninstalledGames();

        assertThat(gameRepository.count()).isEqualTo(1);
    }

    @Test
    void aManualTitleNeverOverridesWhatARicherSourceAlreadyHolds() {
        scanService.submitScan(steamScan("desktop", "1145360", "Hades"));

        Game again = gameIdentityService.resolveOrCreateByTitle("hades", TitleSource.MANIFEST);

        assertSoftly(softly -> {
            softly.assertThat(gameRepository.count()).isEqualTo(1);
            softly.assertThat(again.getTitle()).isEqualTo("Hades");
            softly.assertThat(again.getSteamAppId()).isEqualTo("1145360");
        });
    }

    @Test
    void twoDifferentGamesWithDifferentIgdbIdsStaySeparateEvenWithTheSameTitle() {
        Game remake = gameIdentityService.resolveOrCreateByIgdbId("1", "Prince of Persia", TitleSource.MANIFEST);
        Game original = gameIdentityService.resolveOrCreateByIgdbId("2", "Prince of Persia", TitleSource.MANIFEST);

        assertSoftly(softly -> {
            softly.assertThat(original.getId()).isNotEqualTo(remake.getId());
            softly.assertThat(gameRepository.count()).isEqualTo(2);
        });
    }

    @Test
    void aGameWithoutAnIgdbIdAndOneWithTheSameTitleMergeIntoOne() {
        Game scanned = gameIdentityService.resolveOrCreateByTitle("Celeste", TitleSource.ROM);

        Game picked = gameIdentityService.resolveOrCreateByIgdbId("17000", "Celeste", TitleSource.MANIFEST);

        assertSoftly(softly -> {
            softly.assertThat(picked.getId()).isEqualTo(scanned.getId());
            softly.assertThat(picked.getIgdbGameId()).isEqualTo("17000");
            softly.assertThat(gameRepository.count()).isEqualTo(1);
        });
    }

    @Test
    void theTitleKeyIsTheSameForTheSameGameWrittenDifferently() {
        gameIdentityService.resolveOrCreateByTitle("Super Mario World (USA)", TitleSource.ROM);
        gameIdentityService.resolveOrCreateByTitle("SUPER MARIO WORLD", TitleSource.ROM);

        assertThat(gameRepository.count()).isEqualTo(1);
        assertThat(List.of(UUID.randomUUID())).isNotEmpty();
    }
}
