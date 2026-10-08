package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameLibraryEntry;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleGameEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameEmbeddingRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameMarkRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceRemovalServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final int GRACE_DAYS = 30;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameInstallationRepository gameInstallationRepository;

    @Mock
    private GameLibraryEntryRepository gameLibraryEntryRepository;

    @Mock
    private ConsoleGameEntryRepository consoleGameEntryRepository;

    @Mock
    private GameMarkRepository gameMarkRepository;

    @Mock
    private GameEmbeddingRepository gameEmbeddingRepository;

    @TempDir
    private Path artworkDir;

    private Game game;
    private PlaceRemovalService placeRemovalService;

    @BeforeEach
    void setUp() {
        game = Game.builder().title("Hades").build();
        GameCatalogProperties properties = new GameCatalogProperties(
                new GameCatalogProperties.Artwork(artworkDir.toString(), 2097152L, false, 2000L), GRACE_DAYS,
                new GameCatalogProperties.Enrichment(50, 3), new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Metadata(25, 3), new GameCatalogProperties.Scan(10000, 1_048_576, 262_144, 0.5),
                null);
        placeRemovalService = new PlaceRemovalService(gameRepository, gameInstallationRepository,
                gameLibraryEntryRepository, consoleGameEntryRepository, gameMarkRepository, gameEmbeddingRepository,
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aGameWithAnInstalledOrGraceCopyIsKept() {
        when(gameInstallationRepository.countByGameId(game.getId())).thenReturn(1L);

        boolean removed = placeRemovalService.releaseGameIfOrphaned(game.getId());

        assertThat(removed).isFalse();
        verify(gameRepository, never()).delete(game);
    }

    @Test
    void aGameOnAConsoleIsKept() {
        when(gameInstallationRepository.countByGameId(game.getId())).thenReturn(0L);
        when(consoleGameEntryRepository.existsByGameId(game.getId())).thenReturn(true);

        assertThat(placeRemovalService.releaseGameIfOrphaned(game.getId())).isFalse();
    }

    @Test
    void aGameWithAnActiveLibraryEntryIsKept() {
        when(gameInstallationRepository.countByGameId(game.getId())).thenReturn(0L);
        when(consoleGameEntryRepository.existsByGameId(game.getId())).thenReturn(false);
        when(gameLibraryEntryRepository.existsByGameIdAndRemovedAtIsNull(game.getId())).thenReturn(true);

        assertThat(placeRemovalService.releaseGameIfOrphaned(game.getId())).isFalse();
    }

    @Test
    void aGameWhoseLibraryEntryWasRemovedWithinTheGracePeriodIsKept() {
        Instant cutoff = NOW.minus(GRACE_DAYS, ChronoUnit.DAYS);
        when(gameInstallationRepository.countByGameId(game.getId())).thenReturn(0L);
        when(consoleGameEntryRepository.existsByGameId(game.getId())).thenReturn(false);
        when(gameLibraryEntryRepository.existsByGameIdAndRemovedAtIsNull(game.getId())).thenReturn(false);
        when(gameLibraryEntryRepository.existsByGameIdAndRemovedAtAfter(game.getId(), cutoff)).thenReturn(true);

        assertThat(placeRemovalService.releaseGameIfOrphaned(game.getId())).isFalse();
    }

    @Test
    void anOrphanedGameIsDeletedTogetherWithItsLibraryEntriesMarksAndEmbedding() {
        stubNoPlaceHoldsTheGame();
        List<GameLibraryEntry> expiredEntries = List.of();
        when(gameLibraryEntryRepository.findAllByGameId(game.getId())).thenReturn(expiredEntries);
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));

        boolean removed = placeRemovalService.releaseGameIfOrphaned(game.getId());

        assertThat(removed).isTrue();
        verify(gameLibraryEntryRepository).deleteAll(expiredEntries);
        verify(gameMarkRepository).deleteAllByGameId(game.getId());
        verify(gameEmbeddingRepository).deleteById(game.getId());
        verify(gameRepository).delete(game);
    }

    @Test
    void anOrphanedGameWithAnUploadedCoverLosesItsLocalArtworkFile() throws IOException {
        Path cover = Files.writeString(artworkDir.resolve("hades.png"), "image");
        game.applyCoverArtwork(ArtworkStatus.LOCAL_UPLOAD, "hades.png");
        stubNoPlaceHoldsTheGame();
        when(gameLibraryEntryRepository.findAllByGameId(game.getId())).thenReturn(List.of());
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));

        placeRemovalService.releaseGameIfOrphaned(game.getId());

        assertThat(cover).doesNotExist();
    }

    @Test
    void aGameThatNoLongerExistsIsNotReportedAsRemoved() {
        UUID unknown = UUID.randomUUID();
        when(gameInstallationRepository.countByGameId(unknown)).thenReturn(0L);
        when(consoleGameEntryRepository.existsByGameId(unknown)).thenReturn(false);
        when(gameLibraryEntryRepository.existsByGameIdAndRemovedAtIsNull(unknown)).thenReturn(false);
        when(gameLibraryEntryRepository.existsByGameIdAndRemovedAtAfter(unknown, NOW.minus(GRACE_DAYS, ChronoUnit.DAYS)))
                .thenReturn(false);
        when(gameRepository.findById(unknown)).thenReturn(Optional.empty());

        assertSoftly(softly -> {
            softly.assertThat(placeRemovalService.releaseGameIfOrphaned(unknown)).isFalse();
        });
        verify(gameMarkRepository, never()).deleteAllByGameId(unknown);
    }

    private void stubNoPlaceHoldsTheGame() {
        when(gameInstallationRepository.countByGameId(game.getId())).thenReturn(0L);
        when(consoleGameEntryRepository.existsByGameId(game.getId())).thenReturn(false);
        when(gameLibraryEntryRepository.existsByGameIdAndRemovedAtIsNull(game.getId())).thenReturn(false);
        when(gameLibraryEntryRepository.existsByGameIdAndRemovedAtAfter(game.getId(),
                NOW.minus(GRACE_DAYS, ChronoUnit.DAYS))).thenReturn(false);
    }
}
