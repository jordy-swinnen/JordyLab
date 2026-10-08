package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.service.libbot.FactMatcher.GameFacts;
import dev.jordy.jordylab.gamecatalog.service.libbot.FactMatcher.Verdict;
import dev.jordy.jordylab.gamecatalog.service.libbot.GoldenFixtures.GoldenGame;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** A small library held in memory, for running the whole LibBot pipeline without a database or a model. */
class InMemoryLibrary implements CandidateSource {

    private static final int MAX_CANDIDATES = 15;
    private static final int MAX_UNKNOWN_SAMPLES = 5;

    private final List<GoldenGame> games;
    private final LibraryVocabulary vocabulary;

    InMemoryLibrary(List<GoldenGame> games, LibraryVocabulary vocabulary) {
        this.games = games;
        this.vocabulary = vocabulary;
    }

    @Override
    public LibraryVocabulary vocabulary() {
        return vocabulary;
    }

    @Override
    public long visibleGameCount() {
        return games.size();
    }

    @Override
    public RetrievalResult retrieve(Constraints constraints, String userSubject) {
        boolean asked = GoldenHarness.USER.equals(userSubject);
        List<GoldenGame> confirmed = new ArrayList<>();
        List<GoldenGame> unknown = new ArrayList<>();
        for (GoldenGame game : games) {
            if (!structurallyAllowed(game, constraints) || !markAllowed(game, constraints, asked) || !playable(game)) {
                continue;
            }
            Verdict verdict = FactMatcher.match(new GameFacts(game.maxLocalPlayers(), game.localMultiplayer(),
                    game.singlePlayer(), game.onlineMultiplayer(), game.genres(), game.releaseYear()), constraints);
            if (verdict == Verdict.CONFIRMED) {
                confirmed.add(game);
            } else if (verdict == Verdict.UNKNOWN) {
                unknown.add(game);
            }
        }
        Comparator<GoldenGame> ranking = Comparator
                .comparingInt((GoldenGame game) -> game.wantVotes() + game.likedVotes() - game.dislikedVotes())
                .reversed().thenComparing(game -> game.title().toLowerCase(Locale.ROOT));

        return new RetrievalResult(confirmed.stream().sorted(ranking).limit(MAX_CANDIDATES)
                .map(game -> candidate(game, true, asked)).toList(), unknown.size(),
                unknown.stream().sorted(ranking).limit(MAX_UNKNOWN_SAMPLES).map(game -> candidate(game, false, asked))
                        .toList(), false);
    }

    @Override
    public List<Candidate> describeVisible(List<UUID> gameIds, String userSubject) {
        boolean asked = GoldenHarness.USER.equals(userSubject);

        return gameIds.stream().distinct()
                .flatMap(id -> games.stream().filter(game -> game.id().equals(id)))
                .map(game -> candidate(game, true, asked)).toList();
    }

    /** A game whose only places are emulated copies known to be broken is not offered (spec 013 US13). */
    private boolean playable(GoldenGame game) {
        List<Candidate.RomCopy> copies = game.romCopies() == null ? List.of() : game.romCopies();
        boolean onlyBrokenCopies = !copies.isEmpty()
                && copies.stream().allMatch(copy -> copy.status() == dev.jordy.jordylab.gamecatalog.domain.RomStatus.BROKEN);

        return !onlyBrokenCopies || game.platforms().contains("Steam") || game.platforms().contains("Nintendo Switch");
    }

    /** The same rules the real retriever applies to the asker's marks (spec 013 US10). */
    private boolean markAllowed(GoldenGame game, Constraints constraints, boolean asked) {
        MarkType mine = asked ? game.myMark() : null;
        MarkType filter = constraints.markFilter();
        if (filter == null) {
            return mine != MarkType.PLAYED_DISLIKED && !(constraints.likeMyLiked() && mine == MarkType.PLAYED_LIKED);
        }
        if (constraints.markScope() == MarkScope.MINE) {
            return mine == filter;
        }

        return switch (filter) {
            case WANT_TO_PLAY -> game.wantVotes() > 0;
            case PLAYED_LIKED -> game.likedVotes() > 0;
            case PLAYED_DISLIKED -> game.dislikedVotes() > 0;
        };
    }

    private boolean structurallyAllowed(GoldenGame game, Constraints constraints) {
        if (constraints.installStatus() == InstallFilter.INSTALLED && !game.installed()) {
            return false;
        }
        if (constraints.installStatus() == InstallFilter.NOT_INSTALLED && game.installed()) {
            return false;
        }
        boolean platformOk = constraints.platforms().isEmpty()
                || constraints.platforms().stream().anyMatch(game.platforms()::contains);
        boolean placeOk = constraints.places().isEmpty() || constraints.places().stream().anyMatch(place ->
                ("Living room PC".equals(place) && game.platforms().contains("Steam"))
                        || ("Switch dock".equals(place) && game.platforms().contains("Nintendo Switch")));

        return platformOk && placeOk;
    }

    private Candidate candidate(GoldenGame game, boolean confirmed, boolean asked) {
        return Candidate.builder().gameId(game.id()).title(game.title()).platforms(game.platforms())
                .confirmed(confirmed).wantVotes(game.wantVotes()).likedVotes(game.likedVotes())
                .dislikedVotes(game.dislikedVotes()).genres(game.genres()).releaseYear(game.releaseYear())
                .maxLocalPlayers(game.maxLocalPlayers()).localMultiplayer(game.localMultiplayer())
                .singlePlayer(game.singlePlayer()).onlineMultiplayer(game.onlineMultiplayer())
                .myMark(asked ? game.myMark() : null).romCopies(game.romCopies()).build();
    }
}
