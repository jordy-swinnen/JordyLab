package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameFilter;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourceEnabledResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Turning a scan source off (spec 013 FR-029, confirmed by the owner): the games that live only there disappear from the
 * app, a game that also lives elsewhere stays and shows only its other places, nothing is deleted, and turning the source
 * back on brings everything back as it was.
 */
class DisabledSourceVisibilityTest extends ModuleScenarioSupport {

    private ScanSource sourceOf(String hostname, SourceType type) {
        return hostRepository.findByHostnameIgnoreCase(hostname)
                .flatMap(host -> scanSourceRepository.findByHostIdAndSourceType(host.getId(), type)).orElseThrow();
    }

    private List<String> visibleTitles() {
        GamesPageResponse page = gameQueryService.getGames(GameFilter.everything(), 0, 100);

        return page.content().stream().map(game -> game.title()).sorted().toList();
    }

    @Test
    void disablingTheOnlySourceOfAGameHidesItAndDeletesNothing() {
        scanService.submitScan(emuDeckScan("htpc", "Super Mario World"));
        UUID id = onlyGame().getId();

        scanSourceService.setEnabled(sourceOf("htpc", SourceType.EMUDECK).getId(), false);

        assertSoftly(softly -> {
            softly.assertThat(visibleTitles()).isEmpty();
            softly.assertThat(gameQueryService.getGameDetail(id, null)).isEmpty();
            softly.assertThat(gameRepository.count()).isEqualTo(1);
            softly.assertThat(gameInstallationRepository.count()).isEqualTo(1);
            softly.assertThat(gameQueryService.getPlatforms().platforms()).isEmpty();
        });
    }

    @Test
    void aGameOnTwoSourcesStaysAndShowsOnlyTheRemainingPlace() {
        scanService.submitScan(emuDeckScan("htpc", "Hades"));
        scanService.submitScan(steamScan("desktop", "1145360", "Hades"));

        scanSourceService.setEnabled(sourceOf("htpc", SourceType.EMUDECK).getId(), false);

        GameDetailResponse detail = gameQueryService.getGameDetail(onlyGame().getId(), null).orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(visibleTitles()).containsExactly("Hades");
            softly.assertThat(detail.places()).hasSize(1);
            softly.assertThat(detail.platforms()).extracting(PlatformChip::name).containsExactly("Steam");
            softly.assertThat(gameInstallationRepository.count()).isEqualTo(2);
        });
    }

    @Test
    void enablingTheSourceAgainRestoresEverything() {
        scanService.submitScan(emuDeckScan("htpc", "Hades"));
        scanService.submitScan(steamScan("desktop", "1145360", "Hades"));
        scanService.submitScan(emuDeckScan("laptop", "Celeste"));
        ScanSource emulated = sourceOf("htpc", SourceType.EMUDECK);
        ScanSource laptop = sourceOf("laptop", SourceType.EMUDECK);

        scanSourceService.setEnabled(emulated.getId(), false);
        scanSourceService.setEnabled(laptop.getId(), false);
        assertThat(visibleTitles()).containsExactly("Hades");

        scanSourceService.setEnabled(emulated.getId(), true);
        scanSourceService.setEnabled(laptop.getId(), true);

        Game hades = gameRepository.findBySteamAppId("1145360").orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(visibleTitles()).containsExactly("Celeste", "Hades");
            softly.assertThat(gameQueryService.getGameDetail(hades.getId(), null).orElseThrow().places()).hasSize(2);
        });
    }

    @Test
    void theToggleResponseIsUnchanged() {
        scanService.submitScan(emuDeckScan("htpc", "Hades"));
        UUID sourceId = sourceOf("htpc", SourceType.EMUDECK).getId();

        assertSoftly(softly -> {
            softly.assertThat(scanSourceService.setEnabled(sourceId, false)).contains(new SourceEnabledResponse(sourceId, false));
            softly.assertThat(scanSourceService.setEnabled(sourceId, true)).contains(new SourceEnabledResponse(sourceId, true));
            softly.assertThat(scanSourceService.setEnabled(UUID.randomUUID(), true)).isEmpty();
        });
    }
}
