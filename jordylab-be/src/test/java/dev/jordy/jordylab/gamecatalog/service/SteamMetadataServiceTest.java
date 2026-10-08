package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamAppDetailsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteamMetadataServiceTest {

    private static final int SCAN_CAP = 25;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private SteamAppDetailsClient steamAppDetailsClient;

    private SteamMetadataService steamMetadataService;

    @BeforeEach
    void setUp() {
        steamMetadataService = new SteamMetadataService(gameRepository, steamAppDetailsClient, properties());
    }

    @Test
    void appliesMetadataFromSteamStore() {
        Game game = aSteamGame();
        stubPendingBatch(List.of(game));
        when(steamAppDetailsClient.fetch("620"))
                .thenReturn(Optional.of(new SteamAppDetailsClient.SteamMetadata("Puzzle, Adventure", "Valve",
                        "Valve", 2011, null, "game", null)));

        int processed = steamMetadataService.fetchPending(SCAN_CAP);

        assertThat(processed).isEqualTo(1);
        assertSoftly(softly -> {
            softly.assertThat(game.getGenres()).isEqualTo("Puzzle, Adventure");
            softly.assertThat(game.getDeveloper()).isEqualTo("Valve");
            softly.assertThat(game.getPublisher()).isEqualTo("Valve");
            softly.assertThat(game.getReleaseYear()).isEqualTo(2011);
            softly.assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.OK);
        });
    }

    @Test
    void recordsFailureUntilMaxAttemptsThenMarksFailed() {
        Game game = aSteamGame();
        stubPendingBatch(List.of(game));
        when(steamAppDetailsClient.fetch("620")).thenReturn(Optional.empty());

        steamMetadataService.fetchPending(SCAN_CAP);
        assertSoftly(softly -> {
            softly.assertThat(game.getMetadataAttempts()).isEqualTo(1);
            softly.assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.PENDING);
        });

        steamMetadataService.fetchPending(SCAN_CAP);
        steamMetadataService.fetchPending(SCAN_CAP);
        assertSoftly(softly -> {
            softly.assertThat(game.getMetadataAttempts()).isEqualTo(3);
            softly.assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.FAILED);
        });
    }

    @Test
    void refreshClearsTheFailureCounterAndReFetches() {
        Game game = aSteamGame();
        game.recordMetadataFailure(3);
        when(steamAppDetailsClient.fetch("620"))
                .thenReturn(Optional.of(new SteamAppDetailsClient.SteamMetadata("Puzzle", "Valve", "Valve", 2011, null, "game", null)));

        steamMetadataService.refresh(game);

        assertSoftly(softly -> {
            softly.assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.OK);
            softly.assertThat(game.getMetadataAttempts()).isZero();
            softly.assertThat(game.getDeveloper()).isEqualTo("Valve");
        });
    }

    @Test
    void refreshClearsTheFailureCounterEvenWhenTheFetchFails() {
        Game game = aSteamGame();
        game.recordMetadataFailure(3);
        when(steamAppDetailsClient.fetch("620")).thenReturn(Optional.empty());

        steamMetadataService.refresh(game);

        assertSoftly(softly -> {
            softly.assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.PENDING);
            softly.assertThat(game.getMetadataAttempts()).isEqualTo(1);
        });
    }

    @Test
    void fetchPendingRespectsThePerScanCap() {
        when(gameRepository.findMetadataBacklog(MetadataStatus.PENDING,
                PageRequest.of(0, 7))).thenReturn(List.of());

        int processed = steamMetadataService.fetchPending(7);

        assertThat(processed).isZero();
        verify(gameRepository).findMetadataBacklog(MetadataStatus.PENDING,
                PageRequest.of(0, 7));
    }

    @Test
    void emptyPendingBatchSkipsSteamCalls() {
        stubPendingBatch(List.of());

        int processed = steamMetadataService.fetchPending(SCAN_CAP);

        assertThat(processed).isZero();
        verifyNoInteractions(steamAppDetailsClient);
    }

    private void stubPendingBatch(List<Game> games) {
        when(gameRepository.findMetadataBacklog(MetadataStatus.PENDING,
                PageRequest.of(0, SCAN_CAP)))
                .thenReturn(games);
    }

    private Game aSteamGame() {
        return Game.builder()
                .steamAppId("620")
                .title("Portal 2")
                .build();
    }

    private GameCatalogProperties properties() {
        return new GameCatalogProperties(
                new GameCatalogProperties.Artwork("/tmp/artwork", 2097152L, true, 2000L),
                30,
                new GameCatalogProperties.Enrichment(8, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Metadata(SCAN_CAP, 3),
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144, 0.5), null);
    }
}
