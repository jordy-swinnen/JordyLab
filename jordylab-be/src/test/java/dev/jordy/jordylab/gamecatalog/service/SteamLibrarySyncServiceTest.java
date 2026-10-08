package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.CatalogChanged;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameLibraryEntry;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncOutcome;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncRun;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.LibrarySyncRunRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamFamilyClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamFamilyException;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamOwnedGamesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteamLibrarySyncServiceTest {

    private static final String APP_ID = "620";
    private static final String TITLE = "Portal 2";

    @Mock
    private SteamOwnedGamesClient ownedGamesClient;

    @Mock
    private SteamFamilyClient familyClient;

    @Mock
    private ReconciliationService reconciliationService;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameLibraryEntryRepository libraryEntryRepository;

    @Mock
    private LibrarySyncRunRepository librarySyncRunRepository;

    @Mock
    private GameIdentityService gameIdentityService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SteamLibrarySyncService service;

    @BeforeEach
    void setUp() {
        service = new SteamLibrarySyncService(ownedGamesClient, familyClient, reconciliationService, gameIdentityService,
                gameRepository,
                libraryEntryRepository, librarySyncRunRepository, eventPublisher, properties());
    }

    @Test
    void adoptingAnExistingGameOnlyLinksItAndDoesNoAiOrMetadataWorkForIt() {
        Game existing = aSteamGame();
        when(ownedGamesClient.fetchOwnedGames())
                .thenReturn(Optional.of(List.of(new SteamOwnedGamesClient.OwnedGame(APP_ID, TITLE))));
        when(librarySyncRunRepository.findFirstByLibrarySourceAndOutcomeOrderByFinishedAtDesc(LibrarySource.OWNED,
                LibrarySyncOutcome.APPLIED)).thenReturn(Optional.empty());
        when(libraryEntryRepository.countActiveByLibrarySource(LibrarySource.OWNED)).thenReturn(1L);
        when(gameIdentityService.resolveOrCreateSteamGame(APP_ID, TITLE, TitleSource.LIBRARY)).thenReturn(existing);
        when(libraryEntryRepository.findByGameIdAndLibrarySource(existing.getId(), LibrarySource.OWNED))
                .thenReturn(Optional.of(anEntry(existing)));
        when(libraryEntryRepository.findAllByLibrarySourceAndRemovedAtIsNull(LibrarySource.OWNED)).thenReturn(List.of());

        when(ownedGamesClient.isConfigured()).thenReturn(true);

        service.syncOwned(false);

        ArgumentCaptor<LibrarySyncRun> runCaptor = ArgumentCaptor.forClass(LibrarySyncRun.class);
        verify(librarySyncRunRepository).save(runCaptor.capture());
        assertSoftly(softly -> {
            softly.assertThat(runCaptor.getValue().getOutcome()).isEqualTo(LibrarySyncOutcome.APPLIED);
            softly.assertThat(runCaptor.getValue().getEntriesAdded()).isZero();
            softly.assertThat(runCaptor.getValue().getMetadataCalls()).isZero();
            softly.assertThat(runCaptor.getValue().getAiCalls()).isZero();
        });
        verify(gameIdentityService).resolveOrCreateSteamGame(APP_ID, TITLE, TitleSource.LIBRARY);
    }

    @Test
    void identicalSecondSyncShortCircuitsAsNoChangeWithoutPerGameWork() {
        when(ownedGamesClient.fetchOwnedGames())
                .thenReturn(Optional.of(List.of(new SteamOwnedGamesClient.OwnedGame(APP_ID, TITLE))));
        when(librarySyncRunRepository.findFirstByLibrarySourceAndOutcomeOrderByFinishedAtDesc(LibrarySource.OWNED,
                LibrarySyncOutcome.APPLIED)).thenReturn(Optional.empty());
        when(libraryEntryRepository.countActiveByLibrarySource(LibrarySource.OWNED)).thenReturn(1L);
        Game existing = aSteamGame();
        when(gameIdentityService.resolveOrCreateSteamGame(APP_ID, TITLE, TitleSource.LIBRARY)).thenReturn(existing);
        when(libraryEntryRepository.findByGameIdAndLibrarySource(existing.getId(), LibrarySource.OWNED))
                .thenReturn(Optional.of(anEntry(existing)));
        when(libraryEntryRepository.findAllByLibrarySourceAndRemovedAtIsNull(LibrarySource.OWNED)).thenReturn(List.of());
        when(ownedGamesClient.isConfigured()).thenReturn(true);
        service.syncOwned(false);

        ArgumentCaptor<LibrarySyncRun> firstRun = ArgumentCaptor.forClass(LibrarySyncRun.class);
        verify(librarySyncRunRepository).save(firstRun.capture());
        String hash = firstRun.getValue().getContentHash();

        when(librarySyncRunRepository.findFirstByLibrarySourceAndOutcomeOrderByFinishedAtDesc(LibrarySource.OWNED,
                LibrarySyncOutcome.APPLIED)).thenReturn(Optional.of(aRunWithHash(hash)));
        when(ownedGamesClient.isConfigured()).thenReturn(true);
        service.syncOwned(false);

        ArgumentCaptor<LibrarySyncRun> secondRun = ArgumentCaptor.forClass(LibrarySyncRun.class);
        verify(librarySyncRunRepository, times(2)).save(secondRun.capture());
        assertThat(secondRun.getAllValues().get(1).getOutcome()).isEqualTo(LibrarySyncOutcome.NO_CHANGE);
        verify(gameIdentityService, times(1)).resolveOrCreateSteamGame(APP_ID, TITLE, TitleSource.LIBRARY);
        verify(eventPublisher, times(1)).publishEvent(new CatalogChanged("steam library"));
    }

    @Test
    void emptyLibraryRecordsFailureAndTouchesNoCatalogData() {
        when(ownedGamesClient.fetchOwnedGames()).thenReturn(Optional.of(List.of()));

        when(ownedGamesClient.isConfigured()).thenReturn(true);

        service.syncOwned(false);

        ArgumentCaptor<LibrarySyncRun> runCaptor = ArgumentCaptor.forClass(LibrarySyncRun.class);
        verify(librarySyncRunRepository).save(runCaptor.capture());
        assertSoftly(softly -> {
            softly.assertThat(runCaptor.getValue().getOutcome()).isEqualTo(LibrarySyncOutcome.FAILED);
            softly.assertThat(runCaptor.getValue().getErrorCode()).isEqualTo("EMPTY_RESPONSE");
        });
        verifyNoInteractions(reconciliationService);
        verifyNoInteractions(libraryEntryRepository);
    }

    @Test
    void suspiciousShrinkIsRejectedAndChangesNothing() {
        List<SteamOwnedGamesClient.OwnedGame> tiny = List.of(new SteamOwnedGamesClient.OwnedGame(APP_ID, TITLE));
        when(ownedGamesClient.fetchOwnedGames()).thenReturn(Optional.of(tiny));
        when(librarySyncRunRepository.findFirstByLibrarySourceAndOutcomeOrderByFinishedAtDesc(LibrarySource.OWNED,
                LibrarySyncOutcome.APPLIED)).thenReturn(Optional.empty());
        when(libraryEntryRepository.countActiveByLibrarySource(LibrarySource.OWNED)).thenReturn(500L);

        when(ownedGamesClient.isConfigured()).thenReturn(true);

        service.syncOwned(false);

        ArgumentCaptor<LibrarySyncRun> runCaptor = ArgumentCaptor.forClass(LibrarySyncRun.class);
        verify(librarySyncRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getOutcome()).isEqualTo(LibrarySyncOutcome.SUSPICIOUS);
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void steamFetchFailureRecordsFailedRun() {
        when(ownedGamesClient.fetchOwnedGames()).thenReturn(Optional.empty());

        when(ownedGamesClient.isConfigured()).thenReturn(true);

        service.syncOwned(false);

        ArgumentCaptor<LibrarySyncRun> runCaptor = ArgumentCaptor.forClass(LibrarySyncRun.class);
        verify(librarySyncRunRepository).save(runCaptor.capture());
        assertSoftly(softly -> {
            softly.assertThat(runCaptor.getValue().getOutcome()).isEqualTo(LibrarySyncOutcome.FAILED);
            softly.assertThat(runCaptor.getValue().getErrorCode()).isEqualTo("STEAM_SYNC_FAILED");
        });
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void familySyncOmitsExcludedTitles() {
        when(familyClient.fetchSharedLibrary("token")).thenReturn(List.of(
                new SteamFamilyClient.FamilyGame("730", "Counter-Strike 2", 0, List.of()),
                new SteamFamilyClient.FamilyGame("999", "Not Shareable", 4, List.of())));
        when(librarySyncRunRepository.findFirstByLibrarySourceAndOutcomeOrderByFinishedAtDesc(LibrarySource.FAMILY,
                LibrarySyncOutcome.APPLIED)).thenReturn(Optional.empty());
        when(libraryEntryRepository.countActiveByLibrarySource(LibrarySource.FAMILY)).thenReturn(0L);
        Game game = aSteamGame();
        when(gameIdentityService.resolveOrCreateSteamGame("730", "Counter-Strike 2", TitleSource.LIBRARY))
                .thenReturn(game);
        when(libraryEntryRepository.findByGameIdAndLibrarySource(game.getId(), LibrarySource.FAMILY))
                .thenReturn(Optional.empty());
        when(libraryEntryRepository.findAllByLibrarySourceAndRemovedAtIsNull(LibrarySource.FAMILY)).thenReturn(List.of());

        service.syncFamily("token", false);

        verify(gameIdentityService, never()).resolveOrCreateSteamGame(eq("999"), eq("Not Shareable"),
                eq(TitleSource.LIBRARY));
        verify(gameIdentityService).resolveOrCreateSteamGame("730", "Counter-Strike 2", TitleSource.LIBRARY);
    }

    @Test
    void familyTokenFailureChangesNothing() {
        when(familyClient.fetchSharedLibrary("expired"))
                .thenThrow(new SteamFamilyException("TOKEN_EXPIRED", "expired"));

        service.syncFamily("expired", false);

        ArgumentCaptor<LibrarySyncRun> runCaptor = ArgumentCaptor.forClass(LibrarySyncRun.class);
        verify(librarySyncRunRepository).save(runCaptor.capture());
        assertSoftly(softly -> {
            softly.assertThat(runCaptor.getValue().getOutcome()).isEqualTo(LibrarySyncOutcome.FAILED);
            softly.assertThat(runCaptor.getValue().getErrorCode()).isEqualTo("TOKEN_EXPIRED");
        });
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void toolAppIdsAreExcludedFromTheLibrary() {
        when(ownedGamesClient.fetchOwnedGames()).thenReturn(Optional.of(List.of(
                new SteamOwnedGamesClient.OwnedGame("228980", "Steamworks Common Redistributables"),
                new SteamOwnedGamesClient.OwnedGame(APP_ID, TITLE))));
        when(librarySyncRunRepository.findFirstByLibrarySourceAndOutcomeOrderByFinishedAtDesc(LibrarySource.OWNED,
                LibrarySyncOutcome.APPLIED)).thenReturn(Optional.empty());
        when(libraryEntryRepository.countActiveByLibrarySource(LibrarySource.OWNED)).thenReturn(0L);
        Game game = aSteamGame();
        when(gameIdentityService.resolveOrCreateSteamGame(APP_ID, TITLE, TitleSource.LIBRARY)).thenReturn(game);
        when(libraryEntryRepository.findByGameIdAndLibrarySource(game.getId(), LibrarySource.OWNED))
                .thenReturn(Optional.empty());
        when(libraryEntryRepository.findAllByLibrarySourceAndRemovedAtIsNull(LibrarySource.OWNED)).thenReturn(List.of());

        when(ownedGamesClient.isConfigured()).thenReturn(true);

        service.syncOwned(false);

        verify(gameIdentityService, never()).resolveOrCreateSteamGame(eq("228980"), eq("Steamworks Common Redistributables"),
                eq(TitleSource.LIBRARY));
    }

    @Test
    void unconfiguredOwnedSyncThrowsAndRecordsNothing() {
        when(ownedGamesClient.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.syncOwned(false))
                .isInstanceOf(SteamNotConfiguredException.class);

        verifyNoInteractions(librarySyncRunRepository);
        verifyNoInteractions(reconciliationService);
    }

    private Game aSteamGame() {
        return Game.builder().steamAppId(APP_ID).title(TITLE).titleSource(TitleSource.MANIFEST).build();
    }

    private GameLibraryEntry anEntry(Game game) {
        return GameLibraryEntry.builder().game(game).librarySource(LibrarySource.OWNED)
                .firstSeenAt(java.time.Instant.parse("2026-01-01T00:00:00Z"))
                .lastSeenAt(java.time.Instant.parse("2026-01-01T00:00:00Z")).build();
    }

    private LibrarySyncRun aRunWithHash(String hash) {
        return LibrarySyncRun.builder()
                .librarySource(LibrarySource.OWNED)
                .startedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"))
                .finishedAt(java.time.Instant.parse("2026-01-01T00:00:01Z"))
                .outcome(LibrarySyncOutcome.APPLIED)
                .contentHash(hash)
                .build();
    }

    private GameCatalogProperties properties() {
        return new GameCatalogProperties(
                new GameCatalogProperties.Artwork("/tmp/artwork", 2097152L, true, 2000L),
                30,
                new GameCatalogProperties.Enrichment(8, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Metadata(25, 3),
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144, 0.5),
                new GameCatalogProperties.Library(720, 14, 1500, 10000));
    }
}
