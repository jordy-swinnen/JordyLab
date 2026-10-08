package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.BrandFamily;
import dev.jordy.jordylab.gamecatalog.domain.ConsoleGameEntry;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameMark;
import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleImpactResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.KnownConsoleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/** Registering, naming and removing consoles against the real schema (spec 013 US6, FR-031 to FR-035). */
class ConsoleServiceTest extends ModuleScenarioSupport {

    @Autowired
    private ConsoleService consoleService;

    private void inOneTransaction(Runnable work) {
        transactions.executeWithoutResult(status -> work.run());
    }

    @Test
    void aCatalogPlatformIsAddedUnderItsCanonicalNameAndDefaultsTheConsoleName() {
        ConsoleResponse console = consoleService.add("ps5", null);

        assertSoftly(softly -> {
            softly.assertThat(console.platform()).isEqualTo("PlayStation 5");
            softly.assertThat(console.name()).isEqualTo("PlayStation 5");
            softly.assertThat(console.family()).isEqualTo(BrandFamily.PLAYSTATION);
            softly.assertThat(console.chip().name()).isEqualTo("PlayStation 5");
            softly.assertThat(console.gameCount()).isZero();
        });
    }

    @Test
    void aCustomPlatformIsAcceptedWithANeutralFamily() {
        ConsoleResponse console = consoleService.add("Amiga CD32", "Basement Amiga");

        assertSoftly(softly -> {
            softly.assertThat(console.platform()).isEqualTo("Amiga CD32");
            softly.assertThat(console.family()).isEqualTo(BrandFamily.OTHER);
            softly.assertThat(console.name()).isEqualTo("Basement Amiga");
        });
    }

    @Test
    void theSamePlatformCanBeAddedAgainUnderADifferentName() {
        consoleService.add("Nintendo Switch", "Switch dock");
        consoleService.add("Nintendo Switch", "Switch Lite");

        assertThat(consoleService.list()).extracting(ConsoleResponse::name).containsExactly("Switch dock", "Switch Lite");
    }

    @Test
    void aNameTakenByAnotherConsoleIsRejectedIgnoringCase() {
        consoleService.add("Nintendo Switch", "Switch dock");

        assertThatThrownBy(() -> consoleService.add("Nintendo Switch", "SWITCH DOCK"))
                .isInstanceOf(NameTakenException.class);
    }

    @Test
    void aNameTakenByAHostsDisplayNameIsRejected() {
        hostRepository.save(Host.builder().hostname("cachyos-htpc").displayName("Living room").build());

        assertThatThrownBy(() -> consoleService.add("Nintendo Switch", "living room"))
                .isInstanceOf(NameTakenException.class);
    }

    @Test
    void aNameOverFortyCharactersIsRejected() {
        assertThatThrownBy(() -> consoleService.add("Nintendo Switch", "x".repeat(41)))
                .isInstanceOf(NameTooLongException.class);
    }

    @Test
    void renamingChecksTheNewNameButNotAgainstItself() {
        ConsoleResponse console = consoleService.add("Nintendo Switch", "Switch dock");
        consoleService.add("PlayStation 5", "PS5");

        assertSoftly(softly -> {
            softly.assertThat(consoleService.rename(console.id(), "switch DOCK").name()).isEqualTo("switch DOCK");
            softly.assertThat(consoleService.rename(console.id(), "Bedroom Switch").name()).isEqualTo("Bedroom Switch");
        });
        assertThatThrownBy(() -> consoleService.rename(console.id(), "ps5")).isInstanceOf(NameTakenException.class);
    }

    @Test
    void renamingOrRemovingAnUnknownConsoleFails() {
        assertThatThrownBy(() -> consoleService.rename(UUID.randomUUID(), "x")).isInstanceOf(ConsoleNotFoundException.class);
        assertThatThrownBy(() -> consoleService.remove(UUID.randomUUID())).isInstanceOf(ConsoleNotFoundException.class);
        assertThatThrownBy(() -> consoleService.impact(UUID.randomUUID())).isInstanceOf(ConsoleNotFoundException.class);
    }

    @Test
    void knownConsolesStartAtTheFifthGenerationAndFilterByNameOrAlias() {
        List<KnownConsoleResponse> all = consoleService.known(null);
        List<KnownConsoleResponse> nin = consoleService.known("nin");

        assertSoftly(softly -> {
            softly.assertThat(all).extracting(KnownConsoleResponse::name).contains("PlayStation", "Nintendo 64",
                    "PlayStation 5", "Nintendo Switch").doesNotContain("Super Nintendo", "Atari 2600");
            softly.assertThat(all).extracting(KnownConsoleResponse::generation).isSorted();
            softly.assertThat(nin).extracting(KnownConsoleResponse::name).contains("Nintendo 64", "Nintendo Switch");
            softly.assertThat(consoleService.known("zzzz")).isEmpty();
        });
    }

    @Test
    void removalCountsAndRemovesOnlyTheGamesThatLiveNowhereElse() {
        ConsoleResponse console = consoleService.add("Nintendo Switch", "Switch dock");
        ConsoleResponse other = consoleService.add("Nintendo Switch", "Switch Lite");
        scanService.submitScan(steamScan("desktop", "1145360", "Hades"));
        Game alsoOnSteam = gameRepository.findBySteamAppId("1145360").orElseThrow();
        Game alsoOnOtherConsole = gameIdentityService.resolveOrCreateByTitle("Celeste", TitleSource.MANIFEST);
        Game onlyHere = gameIdentityService.resolveOrCreateByTitle("Mario Kart 8 Deluxe", TitleSource.MANIFEST);
        inOneTransaction(() -> {
            for (Game game : List.of(alsoOnSteam, alsoOnOtherConsole, onlyHere)) {
                consoleGameEntryRepository.save(ConsoleGameEntry.builder().game(game)
                        .console(consoleRepository.findById(console.id()).orElseThrow()).build());
            }
            consoleGameEntryRepository.save(ConsoleGameEntry.builder().game(alsoOnOtherConsole)
                    .console(consoleRepository.findById(other.id()).orElseThrow()).build());
        });
        gameMarkRepository.save(GameMark.builder().game(onlyHere).userSubject("alice").mark(MarkType.WANT_TO_PLAY).build());
        transactions.executeWithoutResult(status -> gameEmbeddingRepository.upsert(onlyHere.getId(), "m", "h",
                GameEmbeddingService.vectorLiteral(new float[1536]), Instant.now()));

        ConsoleImpactResponse impact = consoleService.impact(console.id());
        consoleService.remove(console.id());

        assertSoftly(softly -> {
            softly.assertThat(impact).isEqualTo(new ConsoleImpactResponse(3, 2, 1));
            softly.assertThat(consoleRepository.findById(console.id())).isEmpty();
            softly.assertThat(gameRepository.findById(onlyHere.getId())).isEmpty();
            softly.assertThat(gameRepository.findById(alsoOnSteam.getId())).isPresent();
            softly.assertThat(gameRepository.findById(alsoOnOtherConsole.getId())).isPresent();
            softly.assertThat(gameMarkRepository.count()).isZero();
            softly.assertThat(gameEmbeddingRepository.count()).isZero();
        });
    }
}
