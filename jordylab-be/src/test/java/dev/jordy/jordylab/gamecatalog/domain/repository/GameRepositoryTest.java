package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.InstallationFormat;
import dev.jordy.jordylab.gamecatalog.domain.MetadataStatus;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.shared.config.JpaConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Switch games are tracked by a manual installation on the virtual "Nintendo Switch" source that Flyway seeds
 * (spec 009). These checks run the real visibility queries against PostgreSQL: the grid (T027) and the grounded
 * chat's platform/host vocabulary and row filter (T029) must all include them.
 */
@DataJpaTest
@Testcontainers
@Import(JpaConfiguration.class)
class GameRepositoryTest {

    private static final String SWITCH = "Nintendo Switch";
    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameInstallationRepository installationRepository;

    @Autowired
    private ScanSourceRepository scanSourceRepository;

    private Game switchGame;

    @BeforeEach
    void addASwitchGame() {
        ScanSource switchSource = scanSourceRepository.findBySourceKey(SWITCH).orElseThrow();
        switchGame = gameRepository.save(Game.builder()
                .platform(SWITCH)
                .igdbGameId("1234")
                .title("Mario Kart 8 Deluxe")
                .titleSource(TitleSource.MANUAL)
                .enrichmentStatus(EnrichmentStatus.PENDING)
                .metadataStatus(MetadataStatus.OK)
                .build());
        installationRepository.save(GameInstallation.createManual(switchGame, switchSource,
                switchGame.getId().toString(), InstallationFormat.PHYSICAL, NOW));
    }

    @Test
    void gridListsTheSwitchGameAndFiltersOnTheSwitchHost() {
        List<Game> all = gameRepository.findVisibleGames(null, null, null, "ALL", null, null,
                PageRequest.of(0, 50)).getContent();
        List<Game> onSwitchHost = gameRepository.findVisibleGames(null, null, SWITCH, "INSTALLED", null, null,
                PageRequest.of(0, 50)).getContent();

        assertSoftly(softly -> {
            softly.assertThat(all).extracting(Game::getId).contains(switchGame.getId());
            softly.assertThat(onSwitchHost).extracting(Game::getId).containsExactly(switchGame.getId());
            softly.assertThat(gameRepository.findVisibleById(switchGame.getId())).isPresent();
        });
    }

    @Test
    void switchIsAVisibleHostAndPlatform() {
        assertSoftly(softly -> {
            softly.assertThat(gameRepository.findVisibleHosts()).contains(SWITCH);
            softly.assertThat(gameRepository.findVisiblePlatforms()).contains(SWITCH);
        });
    }

    @Test
    void groundedChatFilterReturnsTheSwitchGame() {
        List<Game> rows = gameRepository.findForChatFilter("mario kart", null, null, null, null, null, null, null,
                null, List.of(SWITCH), List.of(SWITCH), "ALL", null, null, PageRequest.of(0, 50));

        assertThat(rows).extracting(Game::getId).containsExactly(switchGame.getId());
    }
}
