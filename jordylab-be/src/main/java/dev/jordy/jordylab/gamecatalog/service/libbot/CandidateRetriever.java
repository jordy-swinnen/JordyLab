package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.LibBotProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameCandidateRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameCandidateRepository.CandidateRow;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameCandidateRepository.Similarity;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameEmbeddingRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameMarkRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameMarkRepository.VoteTotal;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.service.GameEmbeddingService;
import dev.jordy.jordylab.gamecatalog.service.GamePlatformService;
import dev.jordy.jordylab.gamecatalog.service.libbot.FactMatcher.GameFacts;
import dev.jordy.jordylab.gamecatalog.service.libbot.FactMatcher.Verdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the games a question is about (spec 013 research A5): visible games that satisfy the structural requirements,
 * split by {@link FactMatcher} into confirmed matches and games whose facts are still unknown, ranked best first. A
 * taste or mood text ranks by vector similarity, and by word match when no vector search is possible; without one, by
 * community votes. Votes only order games that already satisfy every requirement. Runs no model call except the one
 * embedding of the question, outside any transaction, and degrades to word search when that fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateRetriever implements CandidateSource {

    private static final String PLACEHOLDER = "";
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int MAX_QUERY_WORDS = 12;

    private final GameRepository gameRepository;
    private final GameCandidateRepository candidateRepository;
    private final GameEmbeddingRepository embeddingRepository;
    private final GameMarkRepository markRepository;
    private final GamePlatformService platformService;
    private final GameEmbeddingService embeddingService;
    private final LibBotProperties properties;

    @Override
    public RetrievalResult retrieve(Constraints constraints, String userSubject) {
        List<CandidateRow> rows = candidateRepository.findStructural(
                constraints.installStatus() == null ? "ALL" : constraints.installStatus().name(),
                constraints.platforms().isEmpty(), listOrPlaceholder(constraints.platforms()),
                constraints.places().isEmpty(), listOrPlaceholder(constraints.places().stream()
                        .map(place -> place.toLowerCase(Locale.ROOT)).toList()));
        Map<UUID, MarkType> myMarks = marksOf(userSubject);
        rows = withinMarkPreferences(rows, constraints, myMarks);
        List<CandidateRow> confirmed = new ArrayList<>();
        List<CandidateRow> unknown = new ArrayList<>();
        for (CandidateRow row : rows) {
            Verdict verdict = FactMatcher.match(factsOf(row), constraints);
            if (verdict == Verdict.CONFIRMED) {
                confirmed.add(row);
            } else if (verdict == Verdict.UNKNOWN) {
                unknown.add(row);
            }
        }
        Playability playability = playabilityOf(Stream.concat(confirmed.stream(), unknown.stream())
                .map(CandidateRow::getId).toList());
        confirmed.removeIf(row -> playability.unplayable().contains(row.getId()));
        unknown.removeIf(row -> playability.unplayable().contains(row.getId()));
        List<UUID> confirmedIds = confirmed.stream().map(CandidateRow::getId).toList();
        Map<UUID, VoteCounts> votes = votesOf(confirmedIds);
        Ranking ranking = rank(tasteOf(constraints, myMarks), confirmedIds);
        List<CandidateRow> ranked = confirmed.stream()
                .sorted(comparator(ranking.scores(), votes, playability.validated()))
                .limit(properties.maxCandidates())
                .toList();
        List<Candidate> confirmedCandidates = describe(ranked, true, ranking.scores(), votes, myMarks, playability);
        List<CandidateRow> unknownRanked = unknown.stream().sorted(comparator(Map.of(), votesOf(
                unknown.stream().map(CandidateRow::getId).toList()), playability.validated()))
                .limit(properties.maxUnknownTitles()).toList();
        List<Candidate> unknownSamples = describe(unknownRanked, false, Map.of(), Map.of(), myMarks, playability);

        return new RetrievalResult(confirmedCandidates, unknown.size(), unknownSamples, ranking.semantic());
    }

    /** The visible games among {@code gameIds} as confirmed candidates (a game the conversation or page points at). */
    @Override
    public List<Candidate> describeVisible(List<UUID> gameIds, String userSubject) {
        if (gameIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, VoteCounts> votes = votesOf(gameIds);
        Map<UUID, MarkType> myMarks = marksOf(userSubject);
        Playability playability = playabilityOf(gameIds);
        Map<UUID, List<String>> platforms = platformService.platformsOf(gameIds);
        Map<UUID, Game> visible = new HashMap<>();
        candidateRepository.findVisibleByIdIn(gameIds).forEach(game -> visible.put(game.getId(), game));

        return gameIds.stream().distinct().map(visible::get).filter(java.util.Objects::nonNull)
                .map(game -> candidateOf(game, platforms.getOrDefault(game.getId(), List.of()), true, null,
                        votes.getOrDefault(game.getId(), VoteCounts.NONE), myMarks.get(game.getId()),
                        playability.copies().getOrDefault(game.getId(), List.of())))
                .toList();
    }

    @Override
    public long visibleGameCount() {
        return candidateRepository.countVisible();
    }

    @Override
    public LibraryVocabulary vocabulary() {
        return new LibraryVocabulary(gameRepository.findVisiblePlatforms(),
                candidateRepository.findVisiblePlaceLabels(), candidateRepository.findLargestKnownLocalGroup());
    }

    private Ranking rank(String semanticQuery, List<UUID> candidateIds) {
        if (!StringUtils.hasText(semanticQuery) || candidateIds.isEmpty()) {
            return new Ranking(Map.of(), false);
        }
        Optional<float[]> queryVector = embeddingService.embedQuery(semanticQuery);
        if (queryVector.isPresent()) {
            Map<UUID, Double> scores = scoresOf(embeddingRepository.similarityTo(
                    GameEmbeddingService.vectorLiteral(queryVector.get()), candidateIds, embeddingService.embeddingModel()));
            if (!scores.isEmpty()) {
                return new Ranking(scores, true);
            }
        }
        String tsQuery = tsQueryOf(semanticQuery);

        return tsQuery.isEmpty() ? new Ranking(Map.of(), false)
                : new Ranking(scoresOf(candidateRepository.rankByWords(candidateIds, tsQuery)), false);
    }

    private Map<UUID, Double> scoresOf(List<Similarity> similarities) {
        Map<UUID, Double> scores = new HashMap<>();
        similarities.forEach(similarity -> scores.put(similarity.getGameId(), similarity.getScore()));

        return scores;
    }

    /** Words only, so nothing a person types can become tsquery syntax. */
    static String tsQueryOf(String text) {
        return java.util.Arrays.stream(NON_WORD.split(text.toLowerCase(Locale.ROOT)))
                .filter(word -> word.length() > 2)
                .distinct()
                .limit(MAX_QUERY_WORDS)
                .collect(java.util.stream.Collectors.joining(" | "));
    }

    /**
     * What the emulated copies mean for whether a game can be played (spec 013 US13): a game whose only places are copies
     * known to be broken is not offered, and one with a copy known to work is preferred over one that is only unverified.
     */
    private Playability playabilityOf(List<UUID> gameIds) {
        if (gameIds.isEmpty()) {
            return new Playability(Map.of(), Set.of(), Set.of());
        }
        Map<UUID, List<Candidate.RomCopy>> copies = new HashMap<>();
        candidateRepository.findEmulatedCopies(gameIds).forEach(copy -> copies
                .computeIfAbsent(copy.getGameId(), id -> new ArrayList<>())
                .add(new Candidate.RomCopy(copy.getMachine(), copy.getRomStatus())));
        Set<UUID> elsewhere = new HashSet<>(candidateRepository.findIdsWithNativeCopy(gameIds));
        elsewhere.addAll(candidateRepository.findIdsOnConsole(gameIds));
        Set<UUID> unplayable = new HashSet<>();
        Set<UUID> validated = new HashSet<>();
        copies.forEach((gameId, gameCopies) -> {
            boolean allBroken = gameCopies.stream().allMatch(copy -> copy.status() == RomStatus.BROKEN);
            if (allBroken && !elsewhere.contains(gameId)) {
                unplayable.add(gameId);
            }
            if (gameCopies.stream().anyMatch(copy -> copy.status() == RomStatus.VALIDATED)) {
                validated.add(gameId);
            }
        });

        return new Playability(copies, unplayable, validated);
    }

    private Comparator<CandidateRow> comparator(Map<UUID, Double> scores, Map<UUID, VoteCounts> votes,
            Set<UUID> validated) {
        Comparator<CandidateRow> byScore = Comparator.comparingDouble(
                (CandidateRow row) -> scores.getOrDefault(row.getId(), Double.NEGATIVE_INFINITY)).reversed();
        Comparator<CandidateRow> byVotes = Comparator.comparingInt(
                (CandidateRow row) -> votes.getOrDefault(row.getId(), VoteCounts.NONE).score()).reversed();
        Comparator<CandidateRow> byValidated = Comparator.comparing((CandidateRow row) -> !validated.contains(row.getId()));
        Comparator<CandidateRow> byTitle = Comparator.comparing(row -> row.getTitle().toLowerCase(Locale.ROOT));

        return scores.isEmpty() ? byValidated.thenComparing(byVotes).thenComparing(byTitle)
                : byScore.thenComparing(byValidated).thenComparing(byVotes).thenComparing(byTitle);
    }

    private List<Candidate> describe(List<CandidateRow> rows, boolean confirmed, Map<UUID, Double> scores,
            Map<UUID, VoteCounts> votes, Map<UUID, MarkType> myMarks, Playability playability) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(CandidateRow::getId).toList();
        Map<UUID, List<String>> platforms = platformService.platformsOf(ids);
        Map<UUID, Game> games = new HashMap<>();
        candidateRepository.findVisibleByIdIn(ids).forEach(game -> games.put(game.getId(), game));

        return rows.stream().filter(row -> games.containsKey(row.getId()))
                .map(row -> candidateOf(games.get(row.getId()), platforms.getOrDefault(row.getId(), List.of()),
                        confirmed, scores.get(row.getId()), votes.getOrDefault(row.getId(), VoteCounts.NONE),
                        myMarks.get(row.getId()), playability.copies().getOrDefault(row.getId(), List.of())))
                .toList();
    }

    private Candidate candidateOf(Game game, List<String> platforms, boolean confirmed, Double similarity,
            VoteCounts votes, MarkType myMark, List<Candidate.RomCopy> romCopies) {
        return Candidate.builder().gameId(game.getId()).title(game.getTitle()).platforms(platforms)
                .confirmed(confirmed).wantVotes(votes.want()).likedVotes(votes.liked())
                .dislikedVotes(votes.disliked()).similarity(similarity)
                .genres(StringUtils.hasText(game.getGenres()) ? game.getGenres() : game.getGenre())
                .developer(game.getDeveloper()).releaseYear(game.getReleaseYear())
                .maxLocalPlayers(game.getMaxLocalPlayers()).localMultiplayer(game.getLocalMultiplayer())
                .singlePlayer(game.getSinglePlayer()).onlineMultiplayer(game.getOnlineMultiplayer())
                .description(shortened(game.getDescription())).myMark(myMark).romCopies(romCopies).build();
    }

    private String shortened(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }

        return description.length() > 400 ? description.substring(0, 400) : description;
    }

    /** The asker's own marks by game; nothing when nobody is asking. */
    private Map<UUID, MarkType> marksOf(String userSubject) {
        Map<UUID, MarkType> marks = new HashMap<>();
        if (StringUtils.hasText(userSubject)) {
            markRepository.findOwnMarks(userSubject).forEach(mark -> marks.put(mark.getGameId(), mark.getMark()));
        }

        return marks;
    }

    /**
     * What the asker's marks mean for a question (spec 013 US10): a mark filter keeps only games with that mark (the
     * asker's own, or anyone's); otherwise games the asker played and disliked are left out, and so are games they
     * liked when they asked for something like what they liked. Votes never decide what is allowed here, only requirements.
     */
    private List<CandidateRow> withinMarkPreferences(List<CandidateRow> rows, Constraints constraints,
            Map<UUID, MarkType> myMarks) {
        MarkType filter = constraints.markFilter();
        if (filter == null) {
            return rows.stream().filter(row -> {
                MarkType mark = myMarks.get(row.getId());

                return mark != MarkType.PLAYED_DISLIKED && !(constraints.likeMyLiked() && mark == MarkType.PLAYED_LIKED);
            }).toList();
        }
        if (constraints.markScope() == MarkScope.MINE) {
            return rows.stream().filter(row -> myMarks.get(row.getId()) == filter).toList();
        }
        Map<UUID, VoteCounts> everyonesVotes = votesOf(rows.stream().map(CandidateRow::getId).toList());

        return rows.stream().filter(row -> everyonesVotes.getOrDefault(row.getId(), VoteCounts.NONE).has(filter)).toList();
    }

    /**
     * The text a result is ranked by meaning against. "Something like what I liked" with no taste words of its own is
     * the titles and genres of the games the asker liked.
     */
    private String tasteOf(Constraints constraints, Map<UUID, MarkType> myMarks) {
        if (StringUtils.hasText(constraints.semanticQuery()) || !constraints.likeMyLiked()) {
            return constraints.semanticQuery();
        }
        List<UUID> likedIds = myMarks.entrySet().stream().filter(entry -> entry.getValue() == MarkType.PLAYED_LIKED)
                .map(Map.Entry::getKey).toList();
        if (likedIds.isEmpty()) {
            return null;
        }

        return candidateRepository.findVisibleByIdIn(likedIds).stream()
                .map(game -> game.getTitle() + (StringUtils.hasText(game.getGenres()) ? " " + game.getGenres() : ""))
                .collect(java.util.stream.Collectors.joining(". "));
    }

    private Map<UUID, VoteCounts> votesOf(List<UUID> gameIds) {
        if (gameIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, VoteCounts> votes = new HashMap<>();
        for (VoteTotal total : markRepository.countVotes(gameIds)) {
            votes.merge(total.getGameId(), VoteCounts.of(total.getMark(), (int) total.getTotal()), VoteCounts::plus);
        }

        return votes;
    }

    private GameFacts factsOf(CandidateRow row) {
        return new GameFacts(row.getMaxLocalPlayers(), row.getLocalMultiplayer(), row.getSinglePlayer(),
                row.getOnlineMultiplayer(), row.getGenres(), row.getReleaseYear());
    }

    private List<String> listOrPlaceholder(List<String> values) {
        return values.isEmpty() ? List.of(PLACEHOLDER) : values;
    }

    private record Playability(Map<UUID, List<Candidate.RomCopy>> copies, Set<UUID> unplayable, Set<UUID> validated) {
    }

    private record Ranking(Map<UUID, Double> scores, boolean semantic) {
    }

    private record VoteCounts(int want, int liked, int disliked) {

        static final VoteCounts NONE = new VoteCounts(0, 0, 0);

        static VoteCounts of(MarkType mark, int total) {
            return switch (mark) {
                case WANT_TO_PLAY -> new VoteCounts(total, 0, 0);
                case PLAYED_LIKED -> new VoteCounts(0, total, 0);
                case PLAYED_DISLIKED -> new VoteCounts(0, 0, total);
            };
        }

        VoteCounts plus(VoteCounts other) {
            return new VoteCounts(want + other.want, liked + other.liked, disliked + other.disliked);
        }

        int score() {
            return want + liked - disliked;
        }

        boolean has(MarkType mark) {
            return switch (mark) {
                case WANT_TO_PLAY -> want > 0;
                case PLAYED_LIKED -> liked > 0;
                case PLAYED_DISLIKED -> disliked > 0;
            };
        }
    }
}
