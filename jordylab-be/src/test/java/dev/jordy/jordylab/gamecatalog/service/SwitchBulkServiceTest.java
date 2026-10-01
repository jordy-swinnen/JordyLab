package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.InstallationFormat;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkConfirmRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkConfirmResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkLine;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SwitchBulkServiceTest {

    private static final UUID EXISTING_GAME_ID = UUID.fromString("0b6f0c9e-6a4d-4c2b-9f3e-1d2c3b4a5e6f");
    private static final UUID NEW_GAME_ID = UUID.fromString("7d8e9f0a-1b2c-4d3e-8f4a-5b6c7d8e9f0a");

    @Mock
    private SwitchGameService switchGameService;

    private SwitchBulkService bulkService;

    @BeforeEach
    void setUp() {
        bulkService = new SwitchBulkService(switchGameService);
    }

    @Test
    void cleansLinesDropsEmptyOnesAndCollapsesDuplicates() {
        List<String> lines = SwitchBulkService.distinctLines("""
                Mario Kart™ 8 Deluxe

                - Splatoon 3
                2. Pikmin 4
                mario kart 8   deluxe
                \tSplatoon 3\t
                """);

        assertThat(lines).containsExactly("Mario Kart 8 Deluxe", "Splatoon 3", "Pikmin 4");
    }

    @Test
    void sameTitleMatchIsTickedAndNothingIsSaved() {
        when(switchGameService.search("Pikmin 4")).thenReturn(List.of(aResult(111L, "Pikmin 4")));
        when(switchGameService.findSwitchGameId("Pikmin 4", 111L)).thenReturn(Optional.empty());

        SwitchBulkLine line = bulkService.preview("Pikmin 4").lines().getFirst();

        assertSoftly(softly -> {
            softly.assertThat(line.status()).isEqualTo(SwitchBulkStatus.MATCH);
            softly.assertThat(line.include()).isTrue();
            softly.assertThat(line.candidates()).extracting(SwitchSearchResult::igdbGameId).containsExactly(111L);
        });
        verify(switchGameService, never()).addFromIgdb(new SwitchGameRequest(111L, "Pikmin 4",
                InstallationFormat.PHYSICAL));
    }

    @Test
    void differentTitleNeedsReviewButStaysTicked() {
        when(switchGameService.search("Zelda Tears")).thenReturn(List.of(
                aResult(222L, "The Legend of Zelda: Tears of the Kingdom")));
        when(switchGameService.findSwitchGameId("Zelda Tears", 222L)).thenReturn(Optional.empty());

        SwitchBulkLine line = bulkService.preview("Zelda Tears").lines().getFirst();

        assertThat(line).extracting(SwitchBulkLine::status, SwitchBulkLine::include)
                .containsExactly(SwitchBulkStatus.NEEDS_REVIEW, true);
    }

    @Test
    void noIgdbResultIsNoMatchAndUnticked() {
        when(switchGameService.search("Some Indie Gem")).thenReturn(List.of());
        when(switchGameService.findSwitchGameId("Some Indie Gem", null)).thenReturn(Optional.empty());

        SwitchBulkLine line = bulkService.preview("Some Indie Gem").lines().getFirst();

        assertThat(line).extracting(SwitchBulkLine::status, SwitchBulkLine::include)
                .containsExactly(SwitchBulkStatus.NO_MATCH, false);
    }

    @Test
    void gameAlreadyOnTheSwitchIsFlaggedWithItsId() {
        when(switchGameService.search("Splatoon 3")).thenReturn(List.of(aResult(333L, "Splatoon 3")));
        when(switchGameService.findSwitchGameId("Splatoon 3", 333L)).thenReturn(Optional.of(EXISTING_GAME_ID));

        SwitchBulkLine line = bulkService.preview("Splatoon 3").lines().getFirst();

        assertThat(line).extracting(SwitchBulkLine::status, SwitchBulkLine::existingGameId, SwitchBulkLine::include)
                .containsExactly(SwitchBulkStatus.ALREADY_PRESENT, EXISTING_GAME_ID, false);
    }

    @Test
    void dlcAndBundlesNeedReviewAndAreUnticked() {
        String dlc = "Mario Kart 8 Deluxe Booster Course Pass DLC";
        when(switchGameService.search(dlc)).thenReturn(List.of(aResult(444L, "Mario Kart 8 Deluxe")));
        when(switchGameService.findSwitchGameId(dlc, 444L)).thenReturn(Optional.empty());

        SwitchBulkLine line = bulkService.preview(dlc).lines().getFirst();

        assertThat(line).extracting(SwitchBulkLine::status, SwitchBulkLine::include)
                .containsExactly(SwitchBulkStatus.NEEDS_REVIEW, false);
    }

    @Test
    void moreThanTheLineLimitIsRejectedBeforeAnyIgdbCall() {
        String text = IntStream.rangeClosed(1, SwitchBulkService.MAX_LINES + 1)
                .mapToObj(number -> "Game " + number)
                .collect(Collectors.joining("\n"));

        assertThatThrownBy(() -> bulkService.preview(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 100");
        verifyNoInteractions(switchGameService);
    }

    @Test
    void confirmAddsTickedLinesAndReportsAlreadyPresentAndSkipped() {
        SwitchBulkConfirmRequest.Item fromIgdb = new SwitchBulkConfirmRequest.Item("Pikmin 4", 111L, null,
                InstallationFormat.DIGITAL);
        SwitchBulkConfirmRequest.Item manual = new SwitchBulkConfirmRequest.Item("Some Indie Gem", null, null,
                InstallationFormat.PHYSICAL);
        SwitchBulkConfirmRequest.Item present = new SwitchBulkConfirmRequest.Item("Splatoon 3", 333L, null,
                InstallationFormat.PHYSICAL);
        SwitchBulkConfirmRequest.Item gone = new SwitchBulkConfirmRequest.Item("Removed Game", 999L, null,
                InstallationFormat.PHYSICAL);
        when(switchGameService.findSwitchGameId("Pikmin 4", 111L)).thenReturn(Optional.empty());
        when(switchGameService.findSwitchGameId("Some Indie Gem", null)).thenReturn(Optional.empty());
        when(switchGameService.findSwitchGameId("Splatoon 3", 333L)).thenReturn(Optional.of(EXISTING_GAME_ID));
        when(switchGameService.findSwitchGameId("Removed Game", 999L)).thenReturn(Optional.empty());
        SwitchGameResponse pikmin = new SwitchGameResponse(NEW_GAME_ID, "Pikmin 4", "Nintendo Switch", "DIGITAL");
        when(switchGameService.addFromIgdb(new SwitchGameRequest(111L, "Pikmin 4", InstallationFormat.DIGITAL)))
                .thenReturn(pikmin);
        SwitchGameResponse indie = new SwitchGameResponse(EXISTING_GAME_ID, "Some Indie Gem", "Nintendo Switch",
                "PHYSICAL");
        when(switchGameService.addManual(new SwitchGameRequest(null, "Some Indie Gem", InstallationFormat.PHYSICAL)))
                .thenReturn(indie);
        when(switchGameService.addFromIgdb(new SwitchGameRequest(999L, "Removed Game", InstallationFormat.PHYSICAL)))
                .thenThrow(new IllegalArgumentException("IGDB game not found"));

        SwitchBulkConfirmResponse summary = bulkService.confirm(
                new SwitchBulkConfirmRequest(List.of(fromIgdb, manual, present, gone)));

        assertSoftly(softly -> {
            softly.assertThat(summary.added()).containsExactly(pikmin, indie);
            softly.assertThat(summary.alreadyPresent()).containsExactly("Splatoon 3");
            softly.assertThat(summary.skipped()).containsExactly(
                    new SwitchBulkConfirmResponse.Skipped("Removed Game", "IGDB game not found"));
        });
    }

    @Test
    void confirmCountsARaceOnTheSameGameAsAlreadyPresent() {
        SwitchBulkConfirmRequest.Item item = new SwitchBulkConfirmRequest.Item("Pikmin 4", 111L, null,
                InstallationFormat.PHYSICAL);
        when(switchGameService.findSwitchGameId("Pikmin 4", 111L)).thenReturn(Optional.empty());
        when(switchGameService.addFromIgdb(new SwitchGameRequest(111L, "Pikmin 4", InstallationFormat.PHYSICAL)))
                .thenThrow(new IllegalStateException("Switch installation already exists"));

        SwitchBulkConfirmResponse summary = bulkService.confirm(new SwitchBulkConfirmRequest(List.of(item)));

        assertThat(summary.alreadyPresent()).containsExactly("Pikmin 4");
    }

    private static SwitchSearchResult aResult(long igdbGameId, String title) {
        return new SwitchSearchResult(igdbGameId, title, 2023, List.of("Strategy"), "Nintendo",
                "https://cover.test/" + igdbGameId + ".jpg", null);
    }
}
