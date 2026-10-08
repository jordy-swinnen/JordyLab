package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameEmbeddingRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.HostRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.LibraryHealthRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HealthExceptionsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibraryHealthResponse;
import dev.jordy.jordylab.shared.config.JpaConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/** The health counts and exception lists against the real schema: only visible games count, and count and list agree. */
@DataJpaTest
@Testcontainers
@Import({JpaConfiguration.class, LibraryHealthService.class})
class LibraryHealthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private LibraryHealthService service;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private ScanSourceRepository scanSourceRepository;

    @Autowired
    private GameInstallationRepository installationRepository;

    @Autowired
    private GameEmbeddingRepository embeddingRepository;

    @Autowired
    private LibraryHealthRepository healthRepository;

    private ScanSource source;

    @BeforeEach
    void setUp() {
        source = scanSourceRepository.save(ScanSource.builder()
                .host(hostRepository.save(Host.builder().hostname("htpc").build())).sourceType(SourceType.STEAM)
                .enabled(true).build());
    }

    private Game visibleGame(String title) {
        Game game = gameRepository.save(Game.builder().title(title).build());
        installationRepository.save(GameInstallation.builder().game(game).source(source).externalRef(title)
                .platform("Steam").firstSeenAt(NOW).lastSeenAt(NOW).build());

        return game;
    }

    @Test
    void countsAndListsWhatIsMissingFromVisibleGamesOnly() {
        Game complete = visibleGame("Complete");
        complete.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, "https://x/c.jpg");
        complete.applyDeterministicDescription("A description.");
        gameRepository.save(complete);
        embeddingRepository.upsert(complete.getId(), "m", "h", GameEmbeddingService.vectorLiteral(new float[1536]), NOW);
        visibleGame("Bare");
        gameRepository.save(Game.builder().title("Invisible").build());

        LibraryHealthResponse health = service.health();
        HealthExceptionsResponse covers = service.exceptions(LibraryHealthService.Kind.COVER);
        HealthExceptionsResponse descriptions = service.exceptions(LibraryHealthService.Kind.DESCRIPTION);
        HealthExceptionsResponse index = service.exceptions(LibraryHealthService.Kind.INDEX);

        assertSoftly(softly -> {
            softly.assertThat(health).isEqualTo(new LibraryHealthResponse(2, 1, 1, 1));
            softly.assertThat(covers.games()).extracting(HealthExceptionsResponse.HealthException::title)
                    .containsExactly("Bare");
            softly.assertThat(covers.total()).isEqualTo(1);
            softly.assertThat(descriptions.games()).extracting(HealthExceptionsResponse.HealthException::title)
                    .containsExactly("Bare");
            softly.assertThat(index.games()).extracting(HealthExceptionsResponse.HealthException::title)
                    .containsExactly("Bare");
            softly.assertThat(healthRepository.countVisible()).isEqualTo(2);
        });
    }
}
