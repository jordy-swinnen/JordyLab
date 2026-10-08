package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.CatalogChanged;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleBulkConfirmRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleBulkPreviewResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleBulkSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleGameListItem;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleGameResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.when;

/** Adding, linking, relinking and removing games on a console (spec 013 US6, FR-034 to FR-036), IGDB mocked. */
@RecordApplicationEvents
class ConsoleGameServiceTest extends ModuleScenarioSupport {

    private static final long MARIO_KART = 13427L;

    @MockitoBean
    private IgdbClient igdbClient;

    @Autowired
    private ConsoleService consoleService;

    @Autowired
    private ConsoleGameService consoleGameService;

    @Autowired
    private ConsoleBulkService consoleBulkService;

    @Autowired
    private ApplicationEvents applicationEvents;

    private ConsoleResponse dock;

    @BeforeEach
    void registerAConsole() {
        dock = consoleService.add("Nintendo Switch", "Switch dock");
    }

    private IgdbClient.IgdbGameDetails details(long id, String title) {
        return new IgdbClient.IgdbGameDetails(id, title, 2017, List.of("Racing"), "Nintendo EPD",
                "https://images.igdb.com/cover.jpg", "https://images.igdb.com/banner.jpg", null);
    }

    @Test
    void aGamePickedFromIgdbIsCreatedWithItsFactsAndCoverAndTheChangeIsAnnounced() {
        when(igdbClient.fetchGameDetails(MARIO_KART)).thenReturn(Optional.of(details(MARIO_KART, "Mario Kart 8 Deluxe")));

        ConsoleGameResponse response = consoleGameService.add(dock.id(), MARIO_KART, null);

        Game game = gameRepository.findById(response.gameId()).orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(response.linkedExisting()).isFalse();
            softly.assertThat(response.title()).isEqualTo("Mario Kart 8 Deluxe");
            softly.assertThat(game.getIgdbGameId()).isEqualTo("13427");
            softly.assertThat(game.getGenres()).isEqualTo("Racing");
            softly.assertThat(game.getReleaseYear()).isEqualTo(2017);
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(consoleService.list().getFirst().gameCount()).isEqualTo(1);
            softly.assertThat(applicationEvents.stream(CatalogChanged.class).count()).isEqualTo(1);
        });
    }

    @Test
    void aTypedTitleWithNoMatchStillCreatesTheGameForTheAutoFillToCompleteLater() {
        ConsoleGameResponse response = consoleGameService.add(dock.id(), null, "Some Homebrew Thing");

        Game game = gameRepository.findById(response.gameId()).orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(response.linkedExisting()).isFalse();
            softly.assertThat(game.getCoverStatus()).isEqualTo(ArtworkStatus.PENDING);
            softly.assertThat(game.getTitleSource()).isEqualTo(TitleSource.MANIFEST);
        });
    }

    @Test
    void theSameGameFromSteamIsLinkedNotDuplicated() {
        scanService.submitScan(steamScan("desktop", "1145360", "Hades"));
        when(igdbClient.fetchGameDetails(119133L)).thenReturn(Optional.of(details(119133L, "Hades")));

        ConsoleGameResponse response = consoleGameService.add(dock.id(), 119133L, null);

        assertSoftly(softly -> {
            softly.assertThat(response.linkedExisting()).isTrue();
            softly.assertThat(gameRepository.count()).isEqualTo(1);
            softly.assertThat(gameRepository.findById(response.gameId()).orElseThrow().getSteamAppId()).isEqualTo("1145360");
            softly.assertThat(gameRepository.findById(response.gameId()).orElseThrow().getIgdbGameId()).isEqualTo("119133");
        });
    }

    @Test
    void addingTheSameGameTwiceToOneConsoleIsRefused() {
        consoleGameService.add(dock.id(), null, "Celeste");

        assertThatThrownBy(() -> consoleGameService.add(dock.id(), null, "celeste"))
                .isInstanceOf(AlreadyOnConsoleException.class);
    }

    @Test
    void anUnknownIgdbIdAndAnEmptyRequestAreRefused() {
        when(igdbClient.fetchGameDetails(1L)).thenReturn(Optional.empty());

        assertSoftly(softly -> {
            softly.assertThatThrownBy(() -> consoleGameService.add(dock.id(), 1L, null))
                    .isInstanceOf(ConsoleGameLookupException.class);
            softly.assertThatThrownBy(() -> consoleGameService.add(dock.id(), null, "  "))
                    .isInstanceOf(ConsoleGameLookupException.class);
            softly.assertThatThrownBy(() -> consoleGameService.add(UUID.randomUUID(), null, "Celeste"))
                    .isInstanceOf(ConsoleNotFoundException.class);
        });
    }

    @Test
    void searchUsesTheConsolesPlatformAndFlagsGamesAlreadyThere() {
        consoleGameService.add(dock.id(), null, "Mario Kart 8 Deluxe");
        when(igdbClient.searchGames("mario", 130L)).thenReturn(List.of(
                new IgdbClient.IgdbSearchResult(MARIO_KART, "Mario Kart 8 Deluxe", 2017, List.of("Racing"), "Nintendo", null, null),
                new IgdbClient.IgdbSearchResult(2L, "Super Mario Odyssey", 2017, List.of("Platformer"), "Nintendo", null, null)));

        List<ConsoleSearchResult> results = consoleGameService.search(dock.id(), "mario");

        assertThat(results).extracting(ConsoleSearchResult::title, ConsoleSearchResult::alreadyOnConsole)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Mario Kart 8 Deluxe", true),
                        org.assertj.core.groups.Tuple.tuple("Super Mario Odyssey", false));
    }

    @Test
    void searchIsEmptyForACustomPlatform() {
        ConsoleResponse custom = consoleService.add("Amiga CD32", null);

        assertThat(consoleGameService.search(custom.id(), "anything")).isEmpty();
    }

    @Test
    void relinkingToAMatchThatAlreadyExistsMergesTheTwoGames() {
        ConsoleGameResponse typed = consoleGameService.add(dock.id(), null, "Mario Cart 8");
        Game existing = gameIdentityService.resolveOrCreateByIgdbId("13427", "Mario Kart 8 Deluxe", TitleSource.MANIFEST);
        when(igdbClient.fetchGameDetails(MARIO_KART)).thenReturn(Optional.of(details(MARIO_KART, "Mario Kart 8 Deluxe")));

        ConsoleGameResponse relinked = consoleGameService.relink(dock.id(), typed.gameId(), MARIO_KART);

        assertSoftly(softly -> {
            softly.assertThat(relinked.gameId()).isEqualTo(existing.getId());
            softly.assertThat(gameRepository.findById(typed.gameId())).isEmpty();
            softly.assertThat(consoleGameService.list(dock.id())).extracting(ConsoleGameListItem::gameId)
                    .containsExactly(existing.getId());
        });
    }

    @Test
    void relinkingToANewMatchCorrectsTheGameInPlace() {
        ConsoleGameResponse typed = consoleGameService.add(dock.id(), null, "Mario Cart 8");
        when(igdbClient.fetchGameDetails(MARIO_KART)).thenReturn(Optional.of(details(MARIO_KART, "Mario Kart 8 Deluxe")));

        ConsoleGameResponse relinked = consoleGameService.relink(dock.id(), typed.gameId(), MARIO_KART);

        Game game = gameRepository.findById(relinked.gameId()).orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(relinked.gameId()).isEqualTo(typed.gameId());
            softly.assertThat(game.getTitle()).isEqualTo("Mario Kart 8 Deluxe");
            softly.assertThat(game.getIgdbGameId()).isEqualTo("13427");
        });
    }

    @Test
    void removingFromOneConsoleKeepsTheGameWhileAnotherPlaceHoldsItAndDeletesItOtherwise() {
        ConsoleResponse lite = consoleService.add("Nintendo Switch", "Switch Lite");
        ConsoleGameResponse shared = consoleGameService.add(dock.id(), null, "Celeste");
        consoleGameService.add(lite.id(), null, "Celeste");
        ConsoleGameResponse onlyHere = consoleGameService.add(dock.id(), null, "Mario Kart 8 Deluxe");

        consoleGameService.remove(dock.id(), shared.gameId());
        consoleGameService.remove(dock.id(), onlyHere.gameId());

        assertSoftly(softly -> {
            softly.assertThat(gameRepository.findById(shared.gameId())).isPresent();
            softly.assertThat(gameRepository.findById(onlyHere.gameId())).isEmpty();
            softly.assertThatThrownBy(() -> consoleGameService.remove(dock.id(), onlyHere.gameId()))
                    .isInstanceOf(GameNotOnConsoleException.class);
        });
    }

    @Test
    void theBulkReviewFlagsMatchedMissingAndAlreadyPresentLinesWithoutSavingAnything() {
        consoleGameService.add(dock.id(), null, "Celeste");
        when(igdbClient.searchGames("Mario Kart 8 Deluxe", 130L)).thenReturn(List.of(
                new IgdbClient.IgdbSearchResult(MARIO_KART, "Mario Kart 8 Deluxe", 2017, List.of(), null, null, null)));
        when(igdbClient.searchGames("Nonsense Quest", 130L)).thenReturn(List.of());

        ConsoleBulkPreviewResponse preview = consoleBulkService.preview(dock.id(),
                List.of("Mario Kart 8 Deluxe", "Nonsense Quest", "Celeste", "  ", "Celeste"));

        assertSoftly(softly -> {
            softly.assertThat(preview.lines()).extracting(ConsoleBulkPreviewResponse.Line::line, ConsoleBulkPreviewResponse.Line::status)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("Mario Kart 8 Deluxe", ConsoleBulkPreviewResponse.Status.MATCHED),
                            org.assertj.core.groups.Tuple.tuple("Nonsense Quest", ConsoleBulkPreviewResponse.Status.NO_MATCH),
                            org.assertj.core.groups.Tuple.tuple("Celeste", ConsoleBulkPreviewResponse.Status.ALREADY_PRESENT));
            softly.assertThat(gameRepository.count()).isEqualTo(1);
        });
    }

    @Test
    void theBulkConfirmAddsTheTickedLinesAndOneBadLineNeverStopsTheOthers() {
        consoleGameService.add(dock.id(), null, "Celeste");
        when(igdbClient.fetchGameDetails(MARIO_KART)).thenReturn(Optional.of(details(MARIO_KART, "Mario Kart 8 Deluxe")));
        when(igdbClient.fetchGameDetails(999L)).thenReturn(Optional.empty());

        ConsoleBulkSummaryResponse summary = consoleBulkService.confirm(dock.id(), List.of(
                new ConsoleBulkConfirmRequest.Item("Mario Kart 8 Deluxe", MARIO_KART, null),
                new ConsoleBulkConfirmRequest.Item("Celeste", null, "Celeste"),
                new ConsoleBulkConfirmRequest.Item("Ghost game", 999L, null),
                new ConsoleBulkConfirmRequest.Item("Hollow Knight", null, "Hollow Knight")));

        assertSoftly(softly -> {
            softly.assertThat(summary.added()).extracting(ConsoleGameResponse::title)
                    .containsExactly("Mario Kart 8 Deluxe", "Hollow Knight");
            softly.assertThat(summary.skipped()).containsExactly("Celeste");
            softly.assertThat(summary.failed()).extracting(ConsoleBulkSummaryResponse.Failure::line).containsExactly("Ghost game");
        });
    }
}
