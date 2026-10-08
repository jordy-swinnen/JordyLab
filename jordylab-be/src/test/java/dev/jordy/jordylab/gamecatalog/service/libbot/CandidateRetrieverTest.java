package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.LibBotProperties;
import dev.jordy.jordylab.gamecatalog.domain.Console;
import dev.jordy.jordylab.gamecatalog.domain.ConsoleGameEntry;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.GameMark;
import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleGameEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameCandidateRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameEmbeddingRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameMarkRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.HostRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.service.GameEmbeddingService;
import dev.jordy.jordylab.gamecatalog.service.GamePlatformService;
import dev.jordy.jordylab.shared.config.JpaConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.when;

/**
 * The retriever against real PostgreSQL and the real schema (spec 013 FR-004, FR-016, research A5): disabled sources
 * hide games, console-only games are visible, unknown facts stay out of the confirmed list, the candidate and unknown
 * caps hold, votes rank only games that already satisfy the requirements, and a failed vector search degrades to words.
 */
@DataJpaTest
@Testcontainers
@Import({JpaConfiguration.class, CandidateRetriever.class, GamePlatformService.class,
        CandidateRetrieverTest.RetrieverConfiguration.class})
class CandidateRetrieverTest {

    private static final String MODEL = "openai/text-embedding-3-small";
    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @TestConfiguration
    static class RetrieverConfiguration {

        @Bean
        LibBotProperties libBotProperties() {
            return new LibBotProperties(10, 2, 10, 15, 5, 1000);
        }
    }

    @MockitoBean
    private GameEmbeddingService embeddingService;

    @Autowired
    private CandidateRetriever retriever;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameCandidateRepository candidateRepository;

    @Autowired
    private GameEmbeddingRepository embeddingRepository;

    @Autowired
    private GameMarkRepository markRepository;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private ScanSourceRepository scanSourceRepository;

    @Autowired
    private GameInstallationRepository installationRepository;

    @Autowired
    private ConsoleRepository consoleRepository;

    @Autowired
    private ConsoleGameEntryRepository consoleGameEntryRepository;

    private ScanSource steamSource;

    @BeforeEach
    void setUp() {
        steamSource = scanSourceRepository.save(ScanSource.builder()
                .host(hostRepository.save(Host.builder().hostname("htpc").displayName("Living room PC").build()))
                .sourceType(SourceType.STEAM).enabled(true).build());
        when(embeddingService.embeddingModel()).thenReturn(MODEL);
        when(embeddingService.embedQuery("cosy cooking")).thenReturn(Optional.empty());
    }

    private static Constraints markConstraints(MarkType mark, MarkScope scope, boolean likeMyLiked) {
        return new Constraints(null, false, false, false, null, null, null, null, null, null, mark, scope, likeMyLiked,
                null);
    }

    private static Constraints sixPlayers() {
        return new Constraints(6, true, false, false, null, null, null, null, null, null, null, null, false, null);
    }

    private Game steamGame(String title, Integer maxPlayers, Boolean local) {
        Game game = gameRepository.save(Game.builder().title(title).build());
        game.applyDeterministicMultiplayer(local, null, maxPlayers, MultiplayerSource.IGDB);
        gameRepository.save(game);
        installationRepository.save(GameInstallation.builder().game(game).source(steamSource)
                .externalRef(title).platform("Steam").firstSeenAt(NOW).lastSeenAt(NOW).build());

        return game;
    }

    private List<String> titles(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::title).toList();
    }

    @Test
    void splitsConfirmedUnknownAndExcludedByWhatIsKnown() {
        steamGame("Eight Players", 8, true);
        steamGame("Two Players", 2, true);
        steamGame("Local Without Count", null, true);
        steamGame("Nothing Known", null, null);
        steamGame("Not Local", null, false);

        RetrievalResult result = retriever.retrieve(sixPlayers(), null);

        assertSoftly(softly -> {
            softly.assertThat(titles(result.confirmed())).containsExactly("Eight Players");
            softly.assertThat(result.unknownCount()).isEqualTo(2);
            softly.assertThat(titles(result.unknownSamples())).containsExactlyInAnyOrder("Local Without Count",
                    "Nothing Known");
            softly.assertThat(result.unknownSamples()).allMatch(candidate -> !candidate.confirmed());
        });
    }

    @Test
    void aDisabledSourceHidesItsGamesFromLibBot() {
        steamGame("Eight Players", 8, true);
        steamSource.setEnabled(false);
        scanSourceRepository.saveAndFlush(steamSource);

        assertThat(retriever.retrieve(sixPlayers(), null).isEmpty()).isTrue();
    }

    @Test
    void aGameOnlyOnAConsoleIsVisibleAndCarriesTheConsolePlatform() {
        Game game = gameRepository.save(Game.builder().title("Mario Kart 8 Deluxe").build());
        game.applyDeterministicMultiplayer(true, true, 4, MultiplayerSource.IGDB);
        gameRepository.save(game);
        Console console = consoleRepository.save(Console.builder().platform("Nintendo Switch").name("Switch dock").build());
        consoleGameEntryRepository.save(ConsoleGameEntry.builder().game(game).console(console).build());

        RetrievalResult result = retriever.retrieve(Constraints.none(), null);

        assertSoftly(softly -> {
            softly.assertThat(titles(result.confirmed())).containsExactly("Mario Kart 8 Deluxe");
            softly.assertThat(result.confirmed().get(0).platforms()).containsExactly("Nintendo Switch");
        });
    }

    @Test
    void capsTheConfirmedAtFifteenAndTheUnknownTitlesAtFiveWithTheFullCount() {
        for (int index = 0; index < 20; index++) {
            steamGame(String.format("Party %02d", index), 8, true);
        }
        for (int index = 0; index < 8; index++) {
            steamGame(String.format("Mystery %02d", index), null, null);
        }

        RetrievalResult result = retriever.retrieve(sixPlayers(), null);

        assertSoftly(softly -> {
            softly.assertThat(result.confirmed()).hasSize(15);
            softly.assertThat(result.unknownCount()).isEqualTo(8);
            softly.assertThat(result.unknownSamples()).hasSize(5);
        });
    }

    @Test
    void votesOrderGamesThatAlreadyPassAndNeverLetAFailingGameIn() {
        Game quiet = steamGame("Alpha Party", 8, true);
        Game wanted = steamGame("Zulu Party", 8, true);
        Game disliked = steamGame("Mike Party", 8, true);
        Game tooSmallButPopular = steamGame("Popular But Small", 2, true);
        markRepository.save(GameMark.builder().game(wanted).userSubject("a").mark(MarkType.WANT_TO_PLAY).build());
        markRepository.save(GameMark.builder().game(wanted).userSubject("b").mark(MarkType.PLAYED_LIKED).build());
        markRepository.save(GameMark.builder().game(disliked).userSubject("a").mark(MarkType.PLAYED_DISLIKED).build());
        for (String user : List.of("a", "b", "c", "d")) {
            markRepository.save(GameMark.builder().game(tooSmallButPopular).userSubject(user)
                    .mark(MarkType.WANT_TO_PLAY).build());
        }

        RetrievalResult result = retriever.retrieve(sixPlayers(), null);

        assertSoftly(softly -> {
            softly.assertThat(titles(result.confirmed())).containsExactly("Zulu Party", "Alpha Party", "Mike Party");
            softly.assertThat(result.confirmed().get(0).wantVotes()).isEqualTo(1);
            softly.assertThat(result.confirmed().get(0).likedVotes()).isEqualTo(1);
            softly.assertThat(result.confirmed().get(2).dislikedVotes()).isEqualTo(1);
            softly.assertThat(quiet).isNotNull();
        });
    }

    @Test
    void aGameTheAskerPlayedAndDislikedIsLeftOutForThemAndKeptForEveryoneElse() {
        Game fine = steamGame("Fine Party", 8, true);
        Game dislikedByAnna = steamGame("Annas Dislike", 8, true);
        markRepository.save(GameMark.builder().game(dislikedByAnna).userSubject("anna")
                .mark(MarkType.PLAYED_DISLIKED).build());

        RetrievalResult forAnna = retriever.retrieve(sixPlayers(), "anna");
        RetrievalResult forBen = retriever.retrieve(sixPlayers(), "ben");

        assertSoftly(softly -> {
            softly.assertThat(titles(forAnna.confirmed())).containsExactly("Fine Party");
            softly.assertThat(titles(forBen.confirmed())).containsExactlyInAnyOrder("Fine Party", "Annas Dislike");
            softly.assertThat(fine).isNotNull();
        });
    }

    @Test
    void theAskersOwnMarkTravelsWithTheCandidate() {
        Game wanted = steamGame("On My List", 8, true);
        markRepository.save(GameMark.builder().game(wanted).userSubject("anna").mark(MarkType.WANT_TO_PLAY).build());

        assertSoftly(softly -> {
            softly.assertThat(retriever.retrieve(sixPlayers(), "anna").confirmed().get(0).myMark())
                    .isEqualTo(MarkType.WANT_TO_PLAY);
            softly.assertThat(retriever.retrieve(sixPlayers(), "ben").confirmed().get(0).myMark()).isNull();
            softly.assertThat(retriever.describeVisible(List.of(wanted.getId()), "anna").get(0).myMark())
                    .isEqualTo(MarkType.WANT_TO_PLAY);
        });
    }

    @Test
    void aMarkFilterOfMineOnlyKeepsMyMarksAndOfEveryoneKeepsAnyonesMarks() {
        Game mine = steamGame("Mine", 8, true);
        Game theirs = steamGame("Theirs", 8, true);
        steamGame("Unmarked", 8, true);
        markRepository.save(GameMark.builder().game(mine).userSubject("anna").mark(MarkType.WANT_TO_PLAY).build());
        markRepository.save(GameMark.builder().game(theirs).userSubject("ben").mark(MarkType.WANT_TO_PLAY).build());
        Constraints myWants = markConstraints(MarkType.WANT_TO_PLAY, MarkScope.MINE, false);
        Constraints everyonesWants = markConstraints(MarkType.WANT_TO_PLAY, MarkScope.ALL, false);

        assertSoftly(softly -> {
            softly.assertThat(titles(retriever.retrieve(myWants, "anna").confirmed())).containsExactly("Mine");
            softly.assertThat(titles(retriever.retrieve(myWants, null).confirmed())).isEmpty();
            softly.assertThat(titles(retriever.retrieve(everyonesWants, "anna").confirmed()))
                    .containsExactlyInAnyOrder("Mine", "Theirs");
        });
    }

    @Test
    void askingForTheDislikedGamesAnswersWithThemInsteadOfHidingThem() {
        Game disliked = steamGame("Never Again", 8, true);
        markRepository.save(GameMark.builder().game(disliked).userSubject("anna").mark(MarkType.PLAYED_DISLIKED).build());

        RetrievalResult result = retriever.retrieve(markConstraints(MarkType.PLAYED_DISLIKED, MarkScope.MINE, false), "anna");

        assertThat(titles(result.confirmed())).containsExactly("Never Again");
    }

    @Test
    void somethingLikeWhatILikedLeavesOutTheGamesAlreadyLikedAndTastesByThem() {
        Game liked = steamGame("Loved Cooking Game", 4, true);
        liked.applyDeterministicMetadata("Cooking", "Dev", "Pub", 2020);
        gameRepository.save(liked);
        Game similar = steamGame("Another Cooking Game", 4, true);
        similar.applyDeterministicMetadata("Cooking", "Dev", "Pub", 2021);
        gameRepository.save(similar);
        steamGame("Unrelated Racer", 4, true);
        markRepository.save(GameMark.builder().game(liked).userSubject("anna").mark(MarkType.PLAYED_LIKED).build());

        RetrievalResult result = retriever.retrieve(Constraints.none().withLikeMyLiked(true), "anna");

        assertSoftly(softly -> {
            softly.assertThat(titles(result.confirmed())).doesNotContain("Loved Cooking Game");
            softly.assertThat(titles(result.confirmed()).get(0)).isEqualTo("Another Cooking Game");
        });
    }

    private ScanSource emulationSource(String hostname, String displayName) {
        return scanSourceRepository.save(ScanSource.builder()
                .host(hostRepository.save(Host.builder().hostname(hostname).displayName(displayName).build()))
                .sourceType(SourceType.EMUDECK).enabled(true).build());
    }

    private Game romOn(String title, ScanSource source, RomStatus status) {
        Game game = gameRepository.save(Game.builder().title(title).build());
        GameInstallation copy = installationRepository.save(GameInstallation.builder().game(game).source(source)
                .externalRef(title).platform("SNES").firstSeenAt(NOW).lastSeenAt(NOW).build());
        copy.changeRomStatus(status);
        installationRepository.save(copy);

        return game;
    }

    @Test
    void aGameWhoseOnlyCopyIsBrokenIsNotOfferedButUnknownAndValidatedCopiesAre() {
        ScanSource htpc = emulationSource("htpc-emu", "Emulation PC");
        romOn("Broken Dream", htpc, RomStatus.BROKEN);
        romOn("Untested", htpc, RomStatus.UNKNOWN);
        romOn("Works Here", htpc, RomStatus.VALIDATED);

        RetrievalResult result = retriever.retrieve(Constraints.none(), null);

        assertThat(titles(result.confirmed())).containsExactlyInAnyOrder("Untested", "Works Here");
    }

    @Test
    void aBrokenCopyDoesNotHideAGameThatCanBePlayedAnotherWay() {
        ScanSource htpc = emulationSource("htpc-emu", "Emulation PC");
        Game twoWays = romOn("Broken Here Fine On Steam", htpc, RomStatus.BROKEN);
        installationRepository.save(GameInstallation.builder().game(twoWays).source(steamSource).externalRef("77")
                .platform("Steam").firstSeenAt(NOW).lastSeenAt(NOW).build());
        Game onConsoleToo = romOn("Broken Here Fine On Switch", htpc, RomStatus.BROKEN);
        Console console = consoleRepository.save(Console.builder().platform("Nintendo Switch").name("Switch dock").build());
        consoleGameEntryRepository.save(ConsoleGameEntry.builder().game(onConsoleToo).console(console).build());

        RetrievalResult result = retriever.retrieve(Constraints.none(), null);

        assertThat(titles(result.confirmed())).containsExactlyInAnyOrder("Broken Here Fine On Steam",
                "Broken Here Fine On Switch");
    }

    @Test
    void oneValidatedMachineKeepsAGameEvenWhenAnotherMachineIsBroken() {
        ScanSource htpc = emulationSource("htpc-emu", "Emulation PC");
        ScanSource laptop = emulationSource("laptop-emu", "Laptop");
        Game game = romOn("Mixed Machines", htpc, RomStatus.VALIDATED);
        GameInstallation brokenCopy = installationRepository.save(GameInstallation.builder().game(game).source(laptop)
                .externalRef("mixed-b").platform("SNES").firstSeenAt(NOW).lastSeenAt(NOW).build());
        brokenCopy.changeRomStatus(RomStatus.BROKEN);
        installationRepository.save(brokenCopy);

        Candidate candidate = retriever.retrieve(Constraints.none(), null).confirmed().getFirst();

        assertSoftly(softly -> {
            softly.assertThat(candidate.title()).isEqualTo("Mixed Machines");
            softly.assertThat(candidate.romCopies()).containsExactlyInAnyOrder(
                    new Candidate.RomCopy("Emulation PC", RomStatus.VALIDATED),
                    new Candidate.RomCopy("Laptop", RomStatus.BROKEN));
        });
    }

    @Test
    void aValidatedCopyIsPreferredOverAnUntestedOneWhenNothingElseSeparatesThem() {
        ScanSource htpc = emulationSource("htpc-emu", "Emulation PC");
        romOn("Aaa Untested", htpc, RomStatus.UNKNOWN);
        romOn("Zzz Validated", htpc, RomStatus.VALIDATED);

        RetrievalResult result = retriever.retrieve(Constraints.none(), null);

        assertThat(titles(result.confirmed())).containsExactly("Zzz Validated", "Aaa Untested");
    }

    @Test
    void installPlatformAndPlaceRequirementsNarrowTheVisibleGames() {
        Game onSteam = steamGame("On Steam", null, null);
        Game onSwitch = gameRepository.save(Game.builder().title("On Switch").build());
        Console console = consoleRepository.save(Console.builder().platform("Nintendo Switch").name("Switch dock").build());
        consoleGameEntryRepository.save(ConsoleGameEntry.builder().game(onSwitch).console(console).build());
        Game libraryOnly = gameRepository.save(Game.builder().title("Library Only").build());
        assertThat(libraryOnly).isNotNull();
        assertThat(onSteam).isNotNull();

        Constraints steamOnly = new Constraints(null, false, false, false, List.of("Steam"), null, null, null, null, null,
                null, null, false, null);
        Constraints dockOnly = new Constraints(null, false, false, false, null, List.of("Switch dock"), null, null, null,
                null, null, null, false, null);
        Constraints livingRoom = new Constraints(null, false, false, false, null, List.of("Living room PC"), null, null,
                null, null, null, null, false, null);

        assertSoftly(softly -> {
            softly.assertThat(titles(retriever.retrieve(steamOnly, null).confirmed())).containsExactly("On Steam");
            softly.assertThat(titles(retriever.retrieve(dockOnly, null).confirmed())).containsExactly("On Switch");
            softly.assertThat(titles(retriever.retrieve(livingRoom, null).confirmed())).containsExactly("On Steam");
        });
    }

    @Test
    void aTasteTextRanksByVectorSimilarityWhenAnEmbeddingExists() {
        Game cooking = steamGame("Kitchen Chaos", 4, true);
        Game racing = steamGame("Asphalt Rush", 4, true);
        embeddingRepository.upsert(cooking.getId(), MODEL, "h1", vector(1f, 0f), NOW);
        embeddingRepository.upsert(racing.getId(), MODEL, "h2", vector(0f, 1f), NOW);
        float[] query = new float[1536];
        query[0] = 1f;
        when(embeddingService.embedQuery("cosy cooking")).thenReturn(Optional.of(query));
        Constraints taste = new Constraints(null, false, false, false, null, null, null, null, null, null, null, null,
                false, "cosy cooking");

        RetrievalResult result = retriever.retrieve(taste, null);

        assertSoftly(softly -> {
            softly.assertThat(titles(result.confirmed())).containsExactly("Kitchen Chaos", "Asphalt Rush");
            softly.assertThat(result.semanticUsed()).isTrue();
            softly.assertThat(result.confirmed().get(0).similarity()).isGreaterThan(result.confirmed().get(1).similarity());
        });
    }

    @Test
    void withoutAnEmbeddingTheTasteTextFallsBackToWordMatching() {
        Game cooking = steamGame("Kitchen Chaos", 4, true);
        cooking.applyDeterministicMetadata("Cooking, Party", "Ghost Town", "Team17", 2016);
        gameRepository.save(cooking);
        steamGame("Asphalt Rush", 4, true);
        Constraints taste = new Constraints(null, false, false, false, null, null, null, null, null, null, null, null,
                false, "cosy cooking");

        RetrievalResult result = retriever.retrieve(taste, null);

        assertSoftly(softly -> {
            softly.assertThat(titles(result.confirmed())).containsExactly("Kitchen Chaos", "Asphalt Rush");
            softly.assertThat(result.semanticUsed()).isFalse();
        });
    }

    @Test
    void theVocabularyListsPlatformsPlacesAndTheLargestKnownGroup() {
        steamGame("Eight Players", 8, true);
        Game onSwitch = gameRepository.save(Game.builder().title("On Switch").build());
        Console console = consoleRepository.save(Console.builder().platform("Nintendo Switch").name("Switch dock").build());
        consoleGameEntryRepository.save(ConsoleGameEntry.builder().game(onSwitch).console(console).build());

        LibraryVocabulary vocabulary = retriever.vocabulary();

        assertSoftly(softly -> {
            softly.assertThat(vocabulary.platforms()).containsExactlyInAnyOrder("Steam", "Nintendo Switch");
            softly.assertThat(vocabulary.places()).containsExactlyInAnyOrder("Living room PC", "Switch dock");
            softly.assertThat(vocabulary.largestKnownGroup()).isEqualTo(8);
        });
    }

    @Test
    void wordQueriesNeverCarryQuerySyntax() {
        assertThat(CandidateRetriever.tsQueryOf("cosy & (cooking) ! 'chaos' | a")).isEqualTo("cosy | cooking | chaos");
    }

    @Test
    void describesOnlyTheVisibleGamesAmongThoseAskedAbout() {
        Game visible = steamGame("Visible", 4, true);
        Game hidden = gameRepository.save(Game.builder().title("Hidden").build());
        UUID unknownId = UUID.randomUUID();

        List<Candidate> described = retriever.describeVisible(List.of(visible.getId(), hidden.getId(), unknownId), null);

        assertThat(titles(described)).containsExactly("Visible");
        assertThat(candidateRepository.countVisible()).isEqualTo(1);
    }

    private static String vector(float first, float second) {
        float[] values = new float[1536];
        values[0] = first;
        values[1] = second;

        return GameEmbeddingService.vectorLiteral(values);
    }
}
