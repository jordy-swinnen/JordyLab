package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameQueryServiceTest {

    private static final Instant SEEN_AT = Instant.parse("2026-08-02T10:15:00Z");
    private static final String PLATFORM = "SNES";

    @Mock
    private GameRepository gameRepository;

    private GameQueryService gameQueryService;

    @BeforeEach
    void setUp() {
        gameQueryService = new GameQueryService(gameRepository);
    }

    @Test
    void mapsVisibleGamesToSummariesWithExternalArtworkUrl() {
        Game game = aGame("Super Mario World");
        game.applyArtwork(ArtworkStatus.EXTERNAL_URL, "https://example.com/smw.png");
        when(gameRepository.findVisibleGames(isNull(), isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(pageOf(List.of(game), 1));

        GamesPageResponse response = gameQueryService.getGames(null, null, 0, 60);

        assertSoftly(softly -> {
            softly.assertThat(response.content()).hasSize(1);
            GameSummaryResponse summary = response.content().getFirst();
            softly.assertThat(summary.id()).isEqualTo(game.getId());
            softly.assertThat(summary.title()).isEqualTo("Super Mario World");
            softly.assertThat(summary.platform()).isEqualTo(PLATFORM);
            softly.assertThat(summary.artworkStatus()).isEqualTo(ArtworkStatus.EXTERNAL_URL);
            softly.assertThat(summary.artworkUrl()).isEqualTo("https://example.com/smw.png");
            softly.assertThat(summary.artworkEndpoint()).isNull();
            softly.assertThat(response.page()).isZero();
            softly.assertThat(response.size()).isEqualTo(60);
            softly.assertThat(response.totalElements()).isEqualTo(1);
            softly.assertThat(response.totalPages()).isEqualTo(1);
        });
    }

    @Test
    void mapsLocalUploadToArtworkEndpointInsteadOfUrl() {
        Game game = aGame("Chrono Trigger");
        game.applyArtwork(ArtworkStatus.LOCAL_UPLOAD, "snes/abc123.png");
        when(gameRepository.findVisibleGames(isNull(), isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(pageOf(List.of(game), 1));

        GamesPageResponse response = gameQueryService.getGames(null, null, 0, 60);

        assertSoftly(softly -> {
            softly.assertThat(response.content().getFirst().artworkUrl()).isNull();
            softly.assertThat(response.content().getFirst().artworkEndpoint())
                    .isEqualTo("/api/gamecatalog/games/" + game.getId() + "/artwork");
        });
    }

    @Test
    void mapsPendingAndPlaceholderArtworkToNullFields() {
        Game pending = aGame("Pending Game");
        Game placeholder = aGame("Placeholder Game");
        placeholder.applyArtwork(ArtworkStatus.PLACEHOLDER, null);
        when(gameRepository.findVisibleGames(isNull(), isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(pageOf(List.of(pending, placeholder), 2));

        GamesPageResponse response = gameQueryService.getGames(null, null, 0, 60);

        assertSoftly(softly -> {
            softly.assertThat(response.content()).allSatisfy(summary -> {
                softly.assertThat(summary.artworkUrl()).isNull();
                softly.assertThat(summary.artworkEndpoint()).isNull();
            });
        });
    }

    @Test
    void passesSearchPlatformAndPaginationToRepository() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(gameRepository.findVisibleGames(eq("mario"), eq(PLATFORM), pageableCaptor.capture()))
                .thenReturn(pageOf(List.of(), 0));

        gameQueryService.getGames("mario", PLATFORM, 2, 60);

        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(2, 60));
    }

    @Test
    void blankSearchAndPlatformBecomeNullForRepository() {
        when(gameRepository.findVisibleGames(isNull(), isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(pageOf(List.of(), 0));

        gameQueryService.getGames(" ", "", 0, 60);

        verify(gameRepository).findVisibleGames(isNull(), isNull(), org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void returnsVisiblePlatforms() {
        when(gameRepository.findVisiblePlatforms()).thenReturn(List.of("SNES", "Steam"));

        PlatformsResponse response = gameQueryService.getPlatforms();

        assertThat(response.platforms()).containsExactly("SNES", "Steam");
    }

    @Test
    void detailExposesEnrichmentFieldsForEnrichedGame() {
        Game game = aGame("Super Mario World");
        game.applyEnrichment("Platformer", 2, false, true, "A classic.");
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));

        Optional<GameDetailResponse> detail = gameQueryService.getGameDetail(game.getId());

        assertSoftly(softly -> {
            softly.assertThat(detail).isPresent();
            softly.assertThat(detail.get().sourceKey()).isEqualTo("snes");
            softly.assertThat(detail.get().genre()).isEqualTo("Platformer");
            softly.assertThat(detail.get().maxLocalPlayers()).isEqualTo(2);
            softly.assertThat(detail.get().description()).isEqualTo("A classic.");
        });
    }

    @Test
    void detailNullsEnrichmentFieldsWhenNotEnriched() {
        Game game = aGame("Super Mario World");
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));

        Optional<GameDetailResponse> detail = gameQueryService.getGameDetail(game.getId());

        assertSoftly(softly -> {
            softly.assertThat(detail).isPresent();
            softly.assertThat(detail.get().enrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
            softly.assertThat(detail.get().genre()).isNull();
            softly.assertThat(detail.get().maxLocalPlayers()).isNull();
            softly.assertThat(detail.get().onlineMultiplayer()).isNull();
            softly.assertThat(detail.get().singlePlayer()).isNull();
            softly.assertThat(detail.get().description()).isNull();
        });
    }

    @Test
    void detailIsEmptyForInvisibleGame() {
        UUID unknownId = UUID.fromString("bbbbbbbb-cccc-4ddd-8eee-ffffffffffff");
        when(gameRepository.findVisibleById(unknownId)).thenReturn(Optional.empty());

        assertThat(gameQueryService.getGameDetail(unknownId)).isEmpty();
    }

    private Page<Game> pageOf(List<Game> games, long total) {
        return new PageImpl<>(games, PageRequest.of(0, 60), total);
    }

    private Game aGame(String title) {
        return Game.builder()
                .source(ScanSource.builder()
                        .sourceKey("snes")
                        .hostname("jordybox")
                        .sourceType(SourceType.EMUDECK)
                        .platform(PLATFORM)
                        .enabled(true)
                        .build())
                .platform(PLATFORM)
                .externalRef(UUID.randomUUID().toString())
                .title(title)
                .firstSeenAt(SEEN_AT)
                .lastSeenAt(SEEN_AT)
                .build();
    }
}
