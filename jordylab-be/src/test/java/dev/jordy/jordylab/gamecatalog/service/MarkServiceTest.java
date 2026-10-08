package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Console;
import dev.jordy.jordylab.gamecatalog.domain.ConsoleGameEntry;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleGameEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameMarkRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.MarkResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.VoteTotalsResponse;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * One mark per person per game, public totals, no voter ever listed (spec 013 US10), against real PostgreSQL so the
 * unique index and the upsert that relies on it are really exercised.
 */
@DataJpaTest
@Testcontainers
@Import({JpaConfiguration.class, MarkService.class})
class MarkServiceTest {

    private static final String ANNA = "6e0f4d8a-3b21-4c57-9e8f-a1b2c3d4e5f6";
    private static final String BEN = "0a1b2c3d-4e5f-4607-8a9b-c0d1e2f3a4b5";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

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

    private Game game;

    @BeforeEach
    void setUp() {
        game = visibleGame("Mario Kart 8 Deluxe");
    }

    @Test
    void settingAMarkCountsItAndReturnsItAsTheCallersOwn() {
        MarkResponse response = markService.setMark(game.getId(), ANNA, MarkType.WANT_TO_PLAY).orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(response.myMark()).isEqualTo(MarkType.WANT_TO_PLAY);
            softly.assertThat(response.votes()).isEqualTo(new VoteTotalsResponse(1, 0, 0));
        });
    }

    @Test
    void choosingAnotherMarkReplacesTheFirstInsteadOfAddingASecondRow() {
        markService.setMark(game.getId(), ANNA, MarkType.WANT_TO_PLAY);

        MarkResponse response = markService.setMark(game.getId(), ANNA, MarkType.PLAYED_LIKED).orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(response.votes()).isEqualTo(new VoteTotalsResponse(0, 1, 0));
            softly.assertThat(gameMarkRepository.findAll()).hasSize(1);
        });
    }

    @Test
    void settingTheSameMarkAgainChangesNothing() {
        markService.setMark(game.getId(), ANNA, MarkType.PLAYED_DISLIKED);

        MarkResponse response = markService.setMark(game.getId(), ANNA, MarkType.PLAYED_DISLIKED).orElseThrow();

        assertThat(response.votes()).isEqualTo(new VoteTotalsResponse(0, 0, 1));
    }

    @Test
    void clearingTheMarkRemovesTheVote() {
        markService.setMark(game.getId(), ANNA, MarkType.WANT_TO_PLAY);

        MarkResponse response = markService.setMark(game.getId(), ANNA, null).orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(response.myMark()).isNull();
            softly.assertThat(response.votes()).isEqualTo(new VoteTotalsResponse(0, 0, 0));
        });
    }

    @Test
    void clearingWhenThereWasNoMarkIsHarmless() {
        MarkResponse response = markService.setMark(game.getId(), ANNA, null).orElseThrow();

        assertThat(response.votes()).isEqualTo(new VoteTotalsResponse(0, 0, 0));
    }

    @Test
    void twoPeopleAreCountedSeparatelyAndOneCannotChangeTheOthersMark() {
        markService.setMark(game.getId(), ANNA, MarkType.WANT_TO_PLAY);
        markService.setMark(game.getId(), BEN, MarkType.WANT_TO_PLAY);
        MarkResponse afterBensChange = markService.setMark(game.getId(), BEN, MarkType.PLAYED_LIKED).orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(afterBensChange.votes()).isEqualTo(new VoteTotalsResponse(1, 1, 0));
            softly.assertThat(gameMarkRepository.findByGameIdAndUserSubject(game.getId(), ANNA).orElseThrow().getMark())
                    .isEqualTo(MarkType.WANT_TO_PLAY);
        });
    }

    @Test
    void theResponseNeverNamesAVoter() {
        markService.setMark(game.getId(), ANNA, MarkType.WANT_TO_PLAY);

        MarkResponse response = markService.setMark(game.getId(), BEN, MarkType.WANT_TO_PLAY).orElseThrow();

        assertThat(response.toString()).doesNotContain(ANNA).doesNotContain(BEN);
    }

    @Test
    void aGameThatIsNotVisibleCannotBeMarked() {
        Game hidden = gameRepository.save(Game.builder().title("Nowhere to be found").build());

        Optional<MarkResponse> response = markService.setMark(hidden.getId(), ANNA, MarkType.WANT_TO_PLAY);

        assertSoftly(softly -> {
            softly.assertThat(response).isEmpty();
            softly.assertThat(gameMarkRepository.findAll()).isEmpty();
        });
    }

    @Test
    void anUnknownGameCannotBeMarked() {
        assertThat(markService.setMark(UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"), ANNA,
                MarkType.WANT_TO_PLAY)).isEmpty();
    }

    @Test
    void totalsCountEachOfTheThreeMarks() {
        markService.setMark(game.getId(), ANNA, MarkType.WANT_TO_PLAY);
        markService.setMark(game.getId(), BEN, MarkType.PLAYED_DISLIKED);
        markService.setMark(game.getId(), "third-user", MarkType.PLAYED_DISLIKED);

        assertThat(markService.totalsOf(game.getId())).isEqualTo(new VoteTotalsResponse(1, 0, 2));
    }

    private Game visibleGame(String title) {
        Game saved = gameRepository.save(Game.builder().title(title).build());
        Console console = consoleRepository.save(Console.builder().platform("Nintendo Switch").name("Switch dock").build());
        consoleGameEntryRepository.save(ConsoleGameEntry.builder().game(saved).console(console).build());

        return saved;
    }
}
