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

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteamMetadataServiceTest {

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
                        "Valve", 2011)));

        steamMetadataService.fetchPendingMetadata();

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

        steamMetadataService.fetchPendingMetadata();
        assertSoftly(softly -> {
            softly.assertThat(game.getMetadataAttempts()).isEqualTo(1);
            softly.assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.PENDING);
        });

        steamMetadataService.fetchPendingMetadata();
        steamMetadataService.fetchPendingMetadata();
        assertSoftly(softly -> {
            softly.assertThat(game.getMetadataAttempts()).isEqualTo(3);
            softly.assertThat(game.getMetadataStatus()).isEqualTo(MetadataStatus.FAILED);
        });
    }

    @Test
    void dailyJobResetsFailedMetadataForRetry() {
        Game failed = aSteamGame();
        failed.recordMetadataFailure(1);
        when(gameRepository.findByMetadataStatus(MetadataStatus.FAILED)).thenReturn(List.of(failed));

        steamMetadataService.resetFailedMetadata();

        assertSoftly(softly -> {
            softly.assertThat(failed.getMetadataStatus()).isEqualTo(MetadataStatus.PENDING);
            softly.assertThat(failed.getMetadataAttempts()).isZero();
        });
    }

    @Test
    void emptyPendingBatchSkipsSteamCalls() {
        stubPendingBatch(List.of());

        steamMetadataService.fetchPendingMetadata();

        verifyNoInteractions(steamAppDetailsClient);
    }

    @Test
    void batchIsLimitedToConfiguredBatchSize() {
        stubPendingBatch(List.of());

        steamMetadataService.fetchPendingMetadata();

        verify(gameRepository).findByMetadataStatusAndSteamAppIdIsNotNull(MetadataStatus.PENDING,
                PageRequest.of(0, 25));
    }

    private void stubPendingBatch(List<Game> games) {
        when(gameRepository.findByMetadataStatusAndSteamAppIdIsNotNull(MetadataStatus.PENDING, PageRequest.of(0, 25)))
                .thenReturn(games);
    }

    private Game aSteamGame() {
        return Game.builder()
                .platform("Steam")
                .steamAppId("620")
                .title("Portal 2")
                .build();
    }

    private GameCatalogProperties properties() {
        return new GameCatalogProperties(
                new GameCatalogProperties.Artwork("/tmp/artwork", 2097152L, true, 2000L),
                30,
                new GameCatalogProperties.Enrichment(50, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Metadata(25, 3),
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144, 0.5));
    }
}
