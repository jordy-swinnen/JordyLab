package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Console;
import dev.jordy.jordylab.gamecatalog.domain.ConsoleGameEntry;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleGameEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameMarkRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.shared.config.JpaConfiguration;
import dev.jordy.jordylab.shared.event.UserAccessRemoved;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * An account that loses access takes its votes with it, and nobody else's (spec 013 FR-046). The listener runs in its own
 * transaction, as module listeners do, so the test commits its data instead of rolling back and cleans up after itself.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DataJpaTest
@Testcontainers
@Import({JpaConfiguration.class, MarkService.class, MarkCleanupListener.class})
class MarkCleanupListenerTest {

    private static final String REMOVED = "6e0f4d8a-3b21-4c57-9e8f-a1b2c3d4e5f6";
    private static final String STAYING = "0a1b2c3d-4e5f-4607-8a9b-c0d1e2f3a4b5";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private MarkCleanupListener listener;

    @Autowired
    private MarkService markService;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameMarkRepository gameMarkRepository;

    @Autowired
    private ConsoleRepository consoleRepository;

    @Autowired
    private ConsoleGameEntryRepository consoleGameEntryRepository;

    @AfterEach
    void cleanUp() {
        gameMarkRepository.deleteAll();
        consoleGameEntryRepository.deleteAll();
        consoleRepository.deleteAll();
        gameRepository.deleteAll();
    }

    @Test
    void removesEveryMarkOfThatAccountAndKeepsTheOthers() {
        Game first = visibleGame("Hades");
        Game second = visibleGame("Celeste");
        markService.setMark(first.getId(), REMOVED, MarkType.WANT_TO_PLAY);
        markService.setMark(second.getId(), REMOVED, MarkType.PLAYED_LIKED);
        markService.setMark(first.getId(), STAYING, MarkType.WANT_TO_PLAY);

        listener.on(new UserAccessRemoved(REMOVED));

        assertSoftly(softly -> {
            softly.assertThat(gameMarkRepository.findAll()).hasSize(1);
            softly.assertThat(markService.totalsOf(first.getId()).wantToPlay()).isEqualTo(1);
            softly.assertThat(markService.totalsOf(second.getId()).playedLiked()).isZero();
        });
    }

    @Test
    void anAccountWithoutMarksIsAHarmlessNoOp() {
        listener.on(new UserAccessRemoved(REMOVED));

        assertSoftly(softly -> softly.assertThat(gameMarkRepository.findAll()).isEmpty());
    }

    private Game visibleGame(String title) {
        Game saved = gameRepository.save(Game.builder().title(title).build());
        Console console = consoleRepository.save(Console.builder().platform("Nintendo Switch").name("Dock " + title).build());
        consoleGameEntryRepository.save(ConsoleGameEntry.builder().game(saved).console(console).build());

        return saved;
    }
}
