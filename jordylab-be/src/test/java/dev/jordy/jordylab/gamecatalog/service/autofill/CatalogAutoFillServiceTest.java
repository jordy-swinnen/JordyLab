package dev.jordy.jordylab.gamecatalog.service.autofill;

import dev.jordy.jordylab.gamecatalog.AutoFillProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameEmbeddingRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.service.GameEmbeddingService;
import dev.jordy.jordylab.gamecatalog.service.GamePlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogAutoFillServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final Instant RETRY_BEFORE = NOW.minusSeconds(24 * 3600);
    private static final PageRequest FIRST_BATCH = PageRequest.of(0, 2);
    private static final UUID PLACEHOLDER_ID = new UUID(0, 0);

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameEmbeddingRepository embeddingRepository;

    @Mock
    private GamePlatformService platformService;

    @Mock
    private FactsStep factsStep;

    @Mock
    private ArtworkStep artworkStep;

    @Mock
    private DescriptionStep descriptionStep;

    @Mock
    private GameEmbeddingService embeddingService;

    private CatalogAutoFillService service;

    @BeforeEach
    void setUp() {
        service = new CatalogAutoFillService(gameRepository, embeddingRepository, platformService, factsStep,
                artworkStep, descriptionStep, embeddingService, new AutoFillProperties(2, 3, 24, null, null),
                TransactionOperations.withoutTransaction(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Game givenAGameInTheBacklog(String title) {
        Game game = Game.builder().title(title).build();
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        when(platformService.platformsOf(game.getId())).thenReturn(List.of("Steam"));
        when(factsStep.needed(game, RETRY_BEFORE)).thenReturn(false);
        when(artworkStep.needed(game, RETRY_BEFORE)).thenReturn(false);

        return game;
    }

    @Test
    void anEmptyBacklogDoesNothingExceptTheSearchIndexSweep() {
        when(gameRepository.findAutoFillBacklog(RETRY_BEFORE, Set.of(PLACEHOLDER_ID), FIRST_BATCH)).thenReturn(List.of());
        when(embeddingService.embeddingModel()).thenReturn("m");
        when(embeddingRepository.findGameIdsMissingOrEmbeddedWithAnotherModel("m")).thenReturn(List.of());

        CatalogAutoFillService.Report report = service.run();

        assertSoftly(softly -> {
            softly.assertThat(report.skipped()).isFalse();
            softly.assertThat(report.processed()).isZero();
        });
    }

    @Test
    void everyGameInTheBacklogGoesThroughTheStepsInOrderAndIsEmbedded() {
        Game first = givenAGameInTheBacklog("First");
        Game second = givenAGameInTheBacklog("Second");
        when(gameRepository.findAutoFillBacklog(RETRY_BEFORE, Set.of(PLACEHOLDER_ID), FIRST_BATCH))
                .thenReturn(List.of(first.getId(), second.getId()));
        when(gameRepository.findAutoFillBacklog(RETRY_BEFORE, Set.of(first.getId(), second.getId()), FIRST_BATCH))
                .thenReturn(List.of());
        when(gameRepository.isAvailableNow(first.getId())).thenReturn(true);
        when(descriptionStep.needed(first, true)).thenReturn(true);
        when(gameRepository.isAvailableNow(second.getId())).thenReturn(true);
        when(descriptionStep.needed(second, true)).thenReturn(false);
        when(embeddingService.embeddingModel()).thenReturn("m");
        when(embeddingRepository.findGameIdsMissingOrEmbeddedWithAnotherModel("m")).thenReturn(List.of());

        CatalogAutoFillService.Report report = service.run();

        assertThat(report.processed()).isEqualTo(2);
        verify(descriptionStep).run(first);
        verify(descriptionStep, never()).run(second);
        verify(embeddingService).embed(List.of(first.getId()));
        verify(embeddingService).embed(List.of(second.getId()));
    }

    @Test
    void oneFailingGameDoesNotStopTheBatch() {
        Game broken = givenAGameInTheBacklog("Broken");
        Game fine = givenAGameInTheBacklog("Fine");
        when(gameRepository.findAutoFillBacklog(RETRY_BEFORE, Set.of(PLACEHOLDER_ID), FIRST_BATCH))
                .thenReturn(List.of(broken.getId(), fine.getId()));
        when(gameRepository.findAutoFillBacklog(RETRY_BEFORE, Set.of(broken.getId(), fine.getId()), FIRST_BATCH))
                .thenReturn(List.of());
        when(gameRepository.isAvailableNow(broken.getId())).thenThrow(new IllegalStateException("database hiccup"));
        when(gameRepository.isAvailableNow(fine.getId())).thenReturn(false);
        when(descriptionStep.needed(fine, false)).thenReturn(false);
        when(embeddingService.embeddingModel()).thenReturn("m");
        when(embeddingRepository.findGameIdsMissingOrEmbeddedWithAnotherModel("m")).thenReturn(List.of());

        CatalogAutoFillService.Report report = service.run();

        assertSoftly(softly -> {
            softly.assertThat(report.processed()).isEqualTo(2);
            softly.assertThat(report.failed()).isEqualTo(1);
        });
        verify(embeddingService).embed(List.of(fine.getId()));
    }

    @Test
    void aGameThatStaysEligibleIsHandledOnlyOncePerRun() {
        Game stubborn = givenAGameInTheBacklog("Stubborn");
        when(gameRepository.findAutoFillBacklog(RETRY_BEFORE, Set.of(PLACEHOLDER_ID), FIRST_BATCH))
                .thenReturn(List.of(stubborn.getId()));
        when(gameRepository.findAutoFillBacklog(RETRY_BEFORE, Set.of(stubborn.getId()), FIRST_BATCH))
                .thenReturn(List.of());
        when(gameRepository.isAvailableNow(stubborn.getId())).thenReturn(false);
        when(descriptionStep.needed(stubborn, false)).thenReturn(false);
        when(embeddingService.embeddingModel()).thenReturn("m");
        when(embeddingRepository.findGameIdsMissingOrEmbeddedWithAnotherModel("m")).thenReturn(List.of());

        service.run();

        verify(embeddingService).embed(List.of(stubborn.getId()));
        verify(gameRepository, times(1)).findAutoFillBacklog(RETRY_BEFORE, Set.of(PLACEHOLDER_ID), FIRST_BATCH);
    }

    @Test
    void aSecondRunWhileOneIsRunningIsSkipped() throws Exception {
        CountDownLatch insideTheFirstRun = new CountDownLatch(1);
        CountDownLatch releaseTheFirstRun = new CountDownLatch(1);
        when(gameRepository.findAutoFillBacklog(RETRY_BEFORE, Set.of(PLACEHOLDER_ID), FIRST_BATCH)).thenAnswer(invocation -> {
            insideTheFirstRun.countDown();
            releaseTheFirstRun.await(10, TimeUnit.SECONDS);

            return List.of();
        });
        when(embeddingService.embeddingModel()).thenReturn("m");
        when(embeddingRepository.findGameIdsMissingOrEmbeddedWithAnotherModel("m")).thenReturn(List.of());
        Thread firstRun = Thread.ofVirtual().start(service::run);
        assertThat(insideTheFirstRun.await(10, TimeUnit.SECONDS)).isTrue();

        CatalogAutoFillService.Report second = service.run();

        releaseTheFirstRun.countDown();
        firstRun.join();
        assertSoftly(softly -> {
            softly.assertThat(second.skipped()).isTrue();
            softly.assertThat(service.isRunning()).isFalse();
        });
    }

    @Test
    void aSteamRateLimitPausesSteamLookupsForTheRestOfTheRun() {
        Game limited = givenAGameInTheBacklog("Limited");
        Game next = givenAGameInTheBacklog("Next");
        when(gameRepository.findAutoFillBacklog(RETRY_BEFORE, Set.of(PLACEHOLDER_ID), FIRST_BATCH))
                .thenReturn(List.of(limited.getId(), next.getId()));
        when(gameRepository.findAutoFillBacklog(RETRY_BEFORE, Set.of(limited.getId(), next.getId()), FIRST_BATCH))
                .thenReturn(List.of());
        when(factsStep.needed(limited, RETRY_BEFORE)).thenReturn(true);
        when(factsStep.needed(next, RETRY_BEFORE)).thenReturn(true);
        when(factsStep.fetch(limited, List.of("Steam"), false))
                .thenReturn(new FactsStep.Fetched(Optional.empty(), Optional.empty(), false, true));
        when(factsStep.fetch(next, List.of("Steam"), true)).thenReturn(FactsStep.Fetched.none());
        when(gameRepository.isAvailableNow(limited.getId())).thenReturn(false);
        when(gameRepository.isAvailableNow(next.getId())).thenReturn(false);
        when(descriptionStep.needed(limited, false)).thenReturn(false);
        when(descriptionStep.needed(next, false)).thenReturn(false);
        when(embeddingService.embeddingModel()).thenReturn("m");
        when(embeddingRepository.findGameIdsMissingOrEmbeddedWithAnotherModel("m")).thenReturn(List.of());

        service.run();

        verify(factsStep).fetch(next, List.of("Steam"), true);
    }
}
