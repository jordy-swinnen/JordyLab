package dev.jordy.jordylab.gamecatalog.service.autofill;

import dev.jordy.jordylab.gamecatalog.domain.DescriptionSource;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.RefreshRun;
import dev.jordy.jordylab.gamecatalog.domain.RefreshRunKind;
import dev.jordy.jordylab.gamecatalog.domain.RefreshRunStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.RefreshRunRepository;
import dev.jordy.jordylab.gamecatalog.service.EnrichmentService;
import dev.jordy.jordylab.gamecatalog.service.GameEmbeddingService;
import dev.jordy.jordylab.gamecatalog.service.GamePlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshRunServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final String ADMIN = "6e0f4d8a-3b21-4c57-9e8f-a1b2c3d4e5f6";

    @Mock
    private RefreshRunRepository runs;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GamePlatformService platformService;

    @Mock
    private FactsStep factsStep;

    @Mock
    private ArtworkStep artworkStep;

    @Mock
    private EnrichmentService enrichmentService;

    @Mock
    private GameEmbeddingService embeddingService;

    private final Map<UUID, RefreshRun> stored = new HashMap<>();
    private final List<Runnable> launched = new ArrayList<>();
    private final List<Game> games = new ArrayList<>();
    private RefreshRunService service;

    @BeforeEach
    void setUp() {
        service = new RefreshRunService(runs, gameRepository, platformService, factsStep, artworkStep, enrichmentService,
                embeddingService, TransactionOperations.withoutTransaction(), launched::add,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(runs.saveAndFlush(org.mockito.ArgumentMatchers.any(RefreshRun.class)))
                .thenAnswer(call -> remember(call.getArgument(0)));
        lenient().when(runs.save(org.mockito.ArgumentMatchers.any(RefreshRun.class)))
                .thenAnswer(call -> remember(call.getArgument(0)));
        lenient().when(runs.findById(org.mockito.ArgumentMatchers.any(UUID.class)))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.<UUID>getArgument(0))));
        lenient().when(gameRepository.findById(org.mockito.ArgumentMatchers.any(UUID.class)))
                .thenAnswer(call -> games.stream().filter(game -> game.getId().equals(call.<UUID>getArgument(0))).findFirst());
        lenient().when(platformService.platformsOf(org.mockito.ArgumentMatchers.any(UUID.class)))
                .thenReturn(List.of("SNES"));
        lenient().when(factsStep.fetch(org.mockito.ArgumentMatchers.any(Game.class),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(FactsStep.Fetched.none());
        lenient().when(artworkStep.fetch(org.mockito.ArgumentMatchers.any(Game.class),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(FactsStep.Fetched.class),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(ArtworkStep.Fetched.none());
    }

    private RefreshRun remember(RefreshRun run) {
        stored.put(run.getId(), run);

        return run;
    }

    private List<Game> visibleGames(int count) {
        for (int index = 0; index < count; index++) {
            games.add(Game.builder().title("Game " + index).build());
        }

        return games;
    }

    private void dataRunSeesEveryGame() {
        when(gameRepository.findAllVisibleIds()).thenReturn(games.stream().map(Game::getId).toList());
    }

    private void everyGameHasNoStoreDescription() {
        when(gameRepository.findVisibleIdsWithoutStoreDescription()).thenReturn(games.stream().map(Game::getId).toList());
    }

    private RefreshRun runNow(RefreshRunKind kind, boolean confirmed) {
        RefreshRun run = service.start(kind, ADMIN, confirmed);
        launched.forEach(Runnable::run);

        return stored.get(run.getId());
    }

    @Test
    void aDataRunVisitsEveryVisibleGameAndEndsSucceeded() {
        visibleGames(3);
        dataRunSeesEveryGame();

        RefreshRun run = runNow(RefreshRunKind.DATA, false);

        assertSoftly(softly -> {
            softly.assertThat(run.getStatus()).isEqualTo(RefreshRunStatus.SUCCEEDED);
            softly.assertThat(run.getTotal()).isEqualTo(3);
            softly.assertThat(run.getProcessed()).isEqualTo(3);
            softly.assertThat(run.getFailed()).isZero();
            softly.assertThat(run.getFailureSummary()).isNull();
            softly.assertThat(run.getFinishedAt()).isEqualTo(NOW);
            softly.assertThat(run.getStartedBy()).isEqualTo(ADMIN);
        });
        verify(embeddingService, times(3)).embed(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void theRunIsVisibleAsRunningWithZeroProgressBeforeItsWorkBegins() {
        visibleGames(2);
        dataRunSeesEveryGame();

        RefreshRun started = service.start(RefreshRunKind.DATA, ADMIN, false);

        assertSoftly(softly -> {
            softly.assertThat(started.getStatus()).isEqualTo(RefreshRunStatus.RUNNING);
            softly.assertThat(started.getTotal()).isEqualTo(2);
            softly.assertThat(started.getProcessed()).isZero();
            softly.assertThat(launched).hasSize(1);
        });
    }

    @Test
    void aSecondStartOfTheSameKindIsRefusedWhileOneIsRunning() {
        when(runs.existsByKindAndStatus(RefreshRunKind.DATA, RefreshRunStatus.RUNNING)).thenReturn(true);

        assertThatThrownBy(() -> service.start(RefreshRunKind.DATA, ADMIN, false))
                .isInstanceOf(RefreshAlreadyActiveException.class);

        assertThat(launched).isEmpty();
    }

    @Test
    void theDatabaseGuardAgainstTwoSimultaneousStartsAlsoRefuses() {
        visibleGames(1);
        dataRunSeesEveryGame();
        when(runs.saveAndFlush(org.mockito.ArgumentMatchers.any(RefreshRun.class)))
                .thenThrow(new DataIntegrityViolationException("uq_refresh_run_running_kind"));

        assertThatThrownBy(() -> service.start(RefreshRunKind.DATA, ADMIN, false))
                .isInstanceOf(RefreshAlreadyActiveException.class);
    }

    @Test
    void theAiRunNeedsTheCostConfirmationAndSaysHowManyPaidCallsItWouldMake() {
        Game first = Game.builder().title("A").build();
        Game second = Game.builder().title("B").build();
        when(gameRepository.findVisibleIdsWithoutStoreDescription()).thenReturn(List.of(first.getId(), second.getId()));

        assertThatThrownBy(() -> service.start(RefreshRunKind.AI, ADMIN, false))
                .isInstanceOfSatisfying(CostConfirmationRequiredException.class,
                        exception -> assertThat(exception.getGames()).isEqualTo(2));

        assertSoftly(softly -> {
            softly.assertThat(launched).isEmpty();
            softly.assertThat(stored).isEmpty();
        });
    }

    @Test
    void aConfirmedAiRunRegeneratesOnlyTheGamesWithoutAStoreDescription() {
        Game written = Game.builder().title("Written by AI").build();
        Game fromSteam = Game.builder().title("Steam text").steamAppId("1").build();
        fromSteam.applyDeterministicDescription("Steam's own words.");
        games.add(written);
        games.add(fromSteam);
        when(gameRepository.findVisibleIdsWithoutStoreDescription()).thenReturn(List.of(written.getId()));
        when(enrichmentService.refreshReporting(written)).thenReturn(Optional.empty());

        RefreshRun run = runNow(RefreshRunKind.AI, true);

        assertSoftly(softly -> {
            softly.assertThat(run.getStatus()).isEqualTo(RefreshRunStatus.SUCCEEDED);
            softly.assertThat(run.getTotal()).isEqualTo(1);
            softly.assertThat(fromSteam.getDescriptionSource()).isEqualTo(DescriptionSource.STEAM);
        });
        verify(enrichmentService, never()).refreshReporting(fromSteam);
    }

    @Test
    void aGameWhoseDescriptionTurnedSteamsOwnMeanwhileIsSkippedNotCharged() {
        Game game = Game.builder().title("Late Steam text").steamAppId("2").build();
        game.applyDeterministicDescription("Steam's own words.");
        games.add(game);
        when(gameRepository.findVisibleIdsWithoutStoreDescription()).thenReturn(List.of(game.getId()));

        RefreshRun run = runNow(RefreshRunKind.AI, true);

        assertThat(run.getStatus()).isEqualTo(RefreshRunStatus.SUCCEEDED);
        verify(enrichmentService, never()).refreshReporting(game);
    }

    @Test
    void oneFailingGameDoesNotStopTheRun() {
        visibleGames(3);
        dataRunSeesEveryGame();
        when(factsStep.fetch(games.get(1), List.of("SNES"), false)).thenThrow(new IllegalStateException("boom"));

        RefreshRun run = runNow(RefreshRunKind.DATA, false);

        assertSoftly(softly -> {
            softly.assertThat(run.getStatus()).isEqualTo(RefreshRunStatus.SUCCEEDED);
            softly.assertThat(run.getProcessed()).isEqualTo(3);
            softly.assertThat(run.getFailed()).isEqualTo(1);
            softly.assertThat(run.getFailureSummary()).contains("1 of 3").contains("IllegalStateException");
        });
    }

    @Test
    void aStopRequestEndsTheRunAfterTheCurrentGame() {
        visibleGames(4);
        everyGameHasNoStoreDescription();
        UUID[] runId = new UUID[1];
        when(enrichmentService.refreshReporting(games.get(0))).thenReturn(Optional.empty());
        when(enrichmentService.refreshReporting(games.get(1))).thenAnswer(call -> {
            service.stop(runId[0]);

            return Optional.empty();
        });

        RefreshRun started = service.start(RefreshRunKind.AI, ADMIN, true);
        runId[0] = started.getId();
        launched.forEach(Runnable::run);
        RefreshRun run = stored.get(runId[0]);

        assertSoftly(softly -> {
            softly.assertThat(run.getStatus()).isEqualTo(RefreshRunStatus.STOPPED);
            softly.assertThat(run.getProcessed()).isEqualTo(2);
            softly.assertThat(run.isStopRequested()).isTrue();
        });
        verify(enrichmentService, never()).refreshReporting(games.get(2));
    }

    @Test
    void fiveFailuresInARowWithTheSameCauseStopTheRunAndSayWhy() {
        visibleGames(8);
        everyGameHasNoStoreDescription();
        games.forEach(game -> lenient().when(enrichmentService.refreshReporting(game)).thenReturn(Optional.of("INSUFFICIENT_CREDITS")));

        RefreshRun run = runNow(RefreshRunKind.AI, true);

        assertSoftly(softly -> {
            softly.assertThat(run.getStatus()).isEqualTo(RefreshRunStatus.FAILED);
            softly.assertThat(run.getProcessed()).isEqualTo(5);
            softly.assertThat(run.getFailed()).isEqualTo(5);
            softly.assertThat(run.getFailureSummary()).contains("INSUFFICIENT_CREDITS").contains("5");
        });
        verify(enrichmentService, never()).refreshReporting(games.get(5));
    }

    @Test
    void differentCausesOrASuccessInBetweenDoNotCountAsTheSameCause() {
        visibleGames(8);
        everyGameHasNoStoreDescription();
        for (int index = 0; index < games.size(); index++) {
            Optional<String> outcome = index == 3 ? Optional.empty()
                    : Optional.of(index % 2 == 0 ? "AUTH_FAILED" : "RATE_LIMITED");
            lenient().when(enrichmentService.refreshReporting(games.get(index))).thenReturn(outcome);
        }

        RefreshRun run = runNow(RefreshRunKind.AI, true);

        assertSoftly(softly -> {
            softly.assertThat(run.getProcessed()).isEqualTo(8);
            softly.assertThat(run.getFailed()).isEqualTo(7);
            softly.assertThat(run.getStatus()).isEqualTo(RefreshRunStatus.SUCCEEDED);
        });
    }

    @Test
    void aRunWhereEveryGameFailedEndsFailed() {
        visibleGames(2);
        dataRunSeesEveryGame();
        games.forEach(game -> when(factsStep.fetch(game, List.of("SNES"), false)).thenThrow(new IllegalStateException("x")));

        RefreshRun run = runNow(RefreshRunKind.DATA, false);

        assertThat(run.getStatus()).isEqualTo(RefreshRunStatus.FAILED);
    }

    @Test
    void aSteamRateLimitCountsAsAFailureWithItsOwnCause() {
        visibleGames(1);
        dataRunSeesEveryGame();
        when(factsStep.fetch(games.get(0), List.of("SNES"), false))
                .thenReturn(new FactsStep.Fetched(Optional.empty(), Optional.empty(), false, true));

        RefreshRun run = runNow(RefreshRunKind.DATA, false);

        assertSoftly(softly -> {
            softly.assertThat(run.getFailed()).isEqualTo(1);
            softly.assertThat(run.getFailureSummary()).contains("STEAM_RATE_LIMITED");
        });
    }

    @Test
    void theDataRunAsksForArtworkThatMayReplaceAnExternalCoverButNeverAnUploadedOne() {
        visibleGames(1);
        dataRunSeesEveryGame();

        runNow(RefreshRunKind.DATA, false);

        verify(artworkStep).fetch(games.get(0), List.of("SNES"), FactsStep.Fetched.none(), true);
        verify(artworkStep).apply(org.mockito.ArgumentMatchers.eq(games.get(0)),
                org.mockito.ArgumentMatchers.eq(ArtworkStep.Fetched.none()),
                org.mockito.ArgumentMatchers.eq(NOW), org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void aRunStillRunningAfterARestartBecomesInterrupted() {
        when(runs.markRunningAsInterrupted(NOW)).thenReturn(1);

        service.interruptLeftoverRuns();

        verify(runs).markRunningAsInterrupted(NOW);
    }

    @Test
    void stoppingAFinishedRunIsRefusedAndAnUnknownRunIsNotFound() {
        RefreshRun finished = RefreshRun.builder().kind(RefreshRunKind.DATA).total(1).startedBy(ADMIN).startedAt(NOW).build();
        finished.finish(RefreshRunStatus.SUCCEEDED, null, NOW);
        stored.put(finished.getId(), finished);

        assertSoftly(softly -> {
            softly.assertThatThrownBy(() -> service.stop(finished.getId())).isInstanceOf(RefreshRunNotRunningException.class);
            softly.assertThatThrownBy(() -> service.stop(UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")))
                    .isInstanceOf(RefreshRunNotFoundException.class);
        });
    }

    @Test
    void currentIsTheLatestRunOfTheKind() {
        RefreshRun latest = RefreshRun.builder().kind(RefreshRunKind.AI).total(5).startedBy(ADMIN).startedAt(NOW).build();
        when(runs.findFirstByKindOrderByStartedAtDesc(RefreshRunKind.AI)).thenReturn(Optional.of(latest));

        assertSoftly(softly -> {
            softly.assertThat(service.current(RefreshRunKind.AI)).contains(latest);
            softly.assertThat(service.current(RefreshRunKind.DATA)).isEmpty();
        });
    }
}
