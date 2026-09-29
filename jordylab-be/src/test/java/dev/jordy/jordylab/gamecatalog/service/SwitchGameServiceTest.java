package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.GameLibraryEntry;
import dev.jordy.jordylab.gamecatalog.domain.InstallationFormat;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameUpdateRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SwitchGameServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T08:00:00Z");
    private static final UUID GAME_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    private static final UUID SOURCE_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

    @Mock
    private IgdbClient igdbClient;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameInstallationRepository installationRepository;

    @Mock
    private GameLibraryEntryRepository libraryEntryRepository;

    @Mock
    private ScanSourceRepository scanSourceRepository;

    @Mock
    private EnrichmentService enrichmentService;

    @InjectMocks
    private SwitchGameService switchGameService;

    private void givenSwitchSource() {
        ScanSource source = ScanSource.builder()
                .id(SOURCE_ID)
                .sourceKey("Nintendo Switch")
                .hostname("Nintendo Switch")
                .sourceType(SourceType.SWITCH)
                .build();
        when(scanSourceRepository.findBySourceKey("Nintendo Switch")).thenReturn(Optional.of(source));
    }

    @Test
    void searchReturnsMappedResults() {
        when(igdbClient.searchSwitchGames("mario")).thenReturn(List.of(
                new IgdbClient.SwitchSearchResult(111L, "Mario Kart 8 Deluxe", 2017, List.of("Racing"),
                        "Nintendo EPD", "https://cover.test/cover.jpg", "https://banner.test/banner.jpg")));

        List<SwitchSearchResult> results = switchGameService.search("mario");

        assertThat(results).hasSize(1);
        SwitchSearchResult result = results.get(0);
        assertThat(result.igdbGameId()).isEqualTo(111L);
        assertThat(result.title()).isEqualTo("Mario Kart 8 Deluxe");
        assertThat(result.coverUrl()).isEqualTo("https://cover.test/cover.jpg");
    }

    @Test
    void addFromIgdbCreatesGameInstallationLibraryEntryAndEnriches() {
        givenSwitchSource();
        when(igdbClient.fetchSwitchGameDetails(111L)).thenReturn(Optional.of(igdbDetails()));
        when(gameRepository.findByPlatformAndIgdbGameId("Nintendo Switch", "111")).thenReturn(Optional.empty());
        when(gameRepository.save(argThat((Game game) -> "Mario Kart 8 Deluxe".equals(game.getTitle()))))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(installationRepository.save(argThat((GameInstallation installation) ->
                SOURCE_ID.equals(installation.getSource().getId()))))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(libraryEntryRepository.findByGameIdAndLibrarySource(argThat((UUID id) -> true), eq(LibrarySource.OWNED)))
                .thenReturn(Optional.empty());

        SwitchGameResponse response = switchGameService.addFromIgdb(
                new SwitchGameRequest(111L, null, InstallationFormat.PHYSICAL));

        assertThat(response.title()).isEqualTo("Mario Kart 8 Deluxe");
        assertThat(response.format()).isEqualTo("PHYSICAL");
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();
        assertThat(savedGame.getTitleSource()).isEqualTo(TitleSource.MANUAL);
        assertThat(savedGame.getMetadataStatus()).isEqualTo(MetadataStatus.OK);
        assertThat(savedGame.getIgdbGameId()).isEqualTo("111");
        assertThat(savedGame.getCoverStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
        assertThat(savedGame.getLocalMultiplayer()).isTrue();
        assertThat(savedGame.getMultiplayerSource()).isEqualTo(MultiplayerSource.IGDB);

        ArgumentCaptor<GameInstallation> installationCaptor = ArgumentCaptor.forClass(GameInstallation.class);
        verify(installationRepository).save(installationCaptor.capture());
        GameInstallation installation = installationCaptor.getValue();
        assertThat(installation.isManual()).isTrue();
        assertThat(installation.getFormat()).isEqualTo(InstallationFormat.PHYSICAL);
        assertThat(installation.getSource().getId()).isEqualTo(SOURCE_ID);

        verify(enrichmentService).refresh(savedGame);
    }

    @Test
    void addFromIgdbExistingGameOnlyAttachesInstallation() {
        givenSwitchSource();
        Game existing = Game.builder().id(GAME_ID).platform("Nintendo Switch").igdbGameId("111")
                .title("Mario Kart 8 Deluxe").titleSource(TitleSource.MANUAL).build();
        when(igdbClient.fetchSwitchGameDetails(111L)).thenReturn(Optional.of(igdbDetails()));
        when(gameRepository.findByPlatformAndIgdbGameId("Nintendo Switch", "111"))
                .thenReturn(Optional.of(existing));
        when(installationRepository.save(argThat((GameInstallation installation) ->
                SOURCE_ID.equals(installation.getSource().getId()))))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(libraryEntryRepository.findByGameIdAndLibrarySource(GAME_ID, LibrarySource.OWNED))
                .thenReturn(Optional.empty());

        switchGameService.addFromIgdb(new SwitchGameRequest(111L, null, InstallationFormat.DIGITAL));

        verify(gameRepository).findByPlatformAndIgdbGameId("Nintendo Switch", "111");
        verifyNoMoreInteractions(gameRepository);
        verifyNoInteractions(enrichmentService);
    }

    @Test
    void addFromIgdbThrowsWhenIgdbGameNotFound() {
        when(igdbClient.fetchSwitchGameDetails(111L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> switchGameService.addFromIgdb(
                new SwitchGameRequest(111L, null, InstallationFormat.PHYSICAL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IGDB game not found");
    }

    @Test
    void addFromIgdbThrowsWhenSwitchInstallationAlreadyExists() {
        givenSwitchSource();
        Game existing = Game.builder().id(GAME_ID).platform("Nintendo Switch").igdbGameId("111")
                .title("Mario Kart 8 Deluxe").titleSource(TitleSource.MANUAL).build();
        when(igdbClient.fetchSwitchGameDetails(111L)).thenReturn(Optional.of(igdbDetails()));
        when(gameRepository.findByPlatformAndIgdbGameId("Nintendo Switch", "111"))
                .thenReturn(Optional.of(existing));
        when(installationRepository.findBySourceIdAndExternalRef(SOURCE_ID, GAME_ID.toString()))
                .thenReturn(Optional.of(GameInstallation.builder().game(existing).source(switchSource())
                        .externalRef(GAME_ID.toString()).firstSeenAt(NOW).lastSeenAt(NOW).build()));

        assertThatThrownBy(() -> switchGameService.addFromIgdb(
                new SwitchGameRequest(111L, null, InstallationFormat.PHYSICAL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Switch installation already exists");
    }

    @Test
    void addManualCreatesCustomGameWithPlaceholderArtwork() {
        givenSwitchSource();
        when(gameRepository.findByPlatformAndLowercaseTitle("Nintendo Switch", "My Custom Game",
                PageRequest.of(0, 1))).thenReturn(List.of());
        when(gameRepository.save(argThat((Game game) -> "My Custom Game".equals(game.getTitle()))))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(installationRepository.save(argThat((GameInstallation installation) ->
                installation.getExternalRef() != null)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(libraryEntryRepository.findByGameIdAndLibrarySource(argThat((UUID id) -> true), eq(LibrarySource.OWNED)))
                .thenReturn(Optional.empty());

        SwitchGameResponse response = switchGameService.addManual(
                new SwitchGameRequest(null, "My Custom Game", InstallationFormat.PHYSICAL));

        assertThat(response.title()).isEqualTo("My Custom Game");
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();
        assertThat(savedGame.getIgdbGameId()).isNull();
        assertThat(savedGame.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.ENRICHED);
        assertThat(savedGame.getCoverStatus()).isEqualTo(ArtworkStatus.PLACEHOLDER);
        verifyNoInteractions(enrichmentService);
    }

    @Test
    void updateFormatChangesInstallationFormatOnly() {
        givenSwitchSource();
        Game game = Game.builder().id(GAME_ID).platform("Nintendo Switch").title("My Game")
                .titleSource(TitleSource.MANUAL).build();
        GameInstallation installation = GameInstallation.builder().game(game).source(switchSource())
                .externalRef(GAME_ID.toString()).format(InstallationFormat.PHYSICAL)
                .firstSeenAt(NOW).lastSeenAt(NOW).build();
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(installationRepository.findBySourceIdAndExternalRef(SOURCE_ID, GAME_ID.toString()))
                .thenReturn(Optional.of(installation));

        SwitchGameResponse response = switchGameService.update(GAME_ID,
                new SwitchGameUpdateRequest(InstallationFormat.DIGITAL, null));

        assertThat(response.format()).isEqualTo("DIGITAL");
        assertThat(installation.getFormat()).isEqualTo(InstallationFormat.DIGITAL);
        verifyNoInteractions(enrichmentService);
    }

    @Test
    void updateIgdbGameIdRefreshesMetadataWithoutEnrichment() {
        givenSwitchSource();
        Game game = Game.builder().id(GAME_ID).platform("Nintendo Switch").title("My Game")
                .titleSource(TitleSource.MANUAL).build();
        GameInstallation installation = GameInstallation.builder().game(game).source(switchSource())
                .externalRef(GAME_ID.toString()).format(InstallationFormat.PHYSICAL)
                .firstSeenAt(NOW).lastSeenAt(NOW).build();
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(installationRepository.findBySourceIdAndExternalRef(SOURCE_ID, GAME_ID.toString()))
                .thenReturn(Optional.of(installation));
        when(igdbClient.fetchSwitchGameDetails(111L)).thenReturn(Optional.of(igdbDetails()));

        switchGameService.update(GAME_ID, new SwitchGameUpdateRequest(null, 111L));

        assertThat(game.getIgdbGameId()).isEqualTo("111");
        assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
        verifyNoInteractions(enrichmentService);
    }

    @Test
    void deleteRemovesInstallationAndGameWhenOrphaned() {
        givenSwitchSource();
        Game game = Game.builder().id(GAME_ID).platform("Nintendo Switch").title("My Game")
                .titleSource(TitleSource.MANUAL).build();
        GameInstallation installation = GameInstallation.builder().game(game).source(switchSource())
                .externalRef(GAME_ID.toString()).format(InstallationFormat.PHYSICAL)
                .firstSeenAt(NOW).lastSeenAt(NOW).build();
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(installationRepository.findBySourceIdAndExternalRef(SOURCE_ID, GAME_ID.toString()))
                .thenReturn(Optional.of(installation));
        when(installationRepository.countByGameId(GAME_ID)).thenReturn(0L);
        when(libraryEntryRepository.existsByGameIdAndRemovedAtIsNull(GAME_ID)).thenReturn(false);
        when(libraryEntryRepository.findAllByGameId(GAME_ID)).thenReturn(List.of(
                GameLibraryEntry.builder().game(game).librarySource(LibrarySource.OWNED)
                        .firstSeenAt(NOW).lastSeenAt(NOW).build()));

        switchGameService.delete(GAME_ID);

        verify(installationRepository).delete(installation);
        ArgumentCaptor<List<GameLibraryEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(libraryEntryRepository).deleteAll(entriesCaptor.capture());
        assertThat(entriesCaptor.getValue()).hasSize(1);
        verify(gameRepository).delete(game);
    }

    @Test
    void deleteKeepsGameWhenAnotherInstallationRemains() {
        givenSwitchSource();
        Game game = Game.builder().id(GAME_ID).platform("Nintendo Switch").title("My Game")
                .titleSource(TitleSource.MANUAL).build();
        GameInstallation installation = GameInstallation.builder().game(game).source(switchSource())
                .externalRef(GAME_ID.toString()).format(InstallationFormat.PHYSICAL)
                .firstSeenAt(NOW).lastSeenAt(NOW).build();
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(installationRepository.findBySourceIdAndExternalRef(SOURCE_ID, GAME_ID.toString()))
                .thenReturn(Optional.of(installation));
        when(installationRepository.countByGameId(GAME_ID)).thenReturn(1L);
        when(libraryEntryRepository.existsByGameIdAndRemovedAtIsNull(GAME_ID)).thenReturn(false);

        switchGameService.delete(GAME_ID);

        verify(installationRepository).delete(installation);
        verify(libraryEntryRepository, never()).deleteAll(entriesCaptorOrEmpty());
        verify(gameRepository, never()).delete(game);
    }

    private List<GameLibraryEntry> entriesCaptorOrEmpty() {
        return ArgumentCaptor.forClass(List.class).capture();
    }

    private IgdbClient.SwitchGameDetails igdbDetails() {
        return new IgdbClient.SwitchGameDetails(111L, "Mario Kart 8 Deluxe", 2017, List.of("Racing"),
                "Nintendo EPD", "https://cover.test/cover.jpg", "https://banner.test/banner.jpg",
                new IgdbClient.MultiplayerMode(true, true, false, 4));
    }

    private ScanSource switchSource() {
        return ScanSource.builder()
                .id(SOURCE_ID)
                .hostname("Nintendo Switch")
                .sourceType(SourceType.SWITCH)
                .build();
    }
}
