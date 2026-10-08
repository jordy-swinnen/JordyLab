package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import dev.jordy.jordylab.gamecatalog.service.libbot.AppliedConstraint.Kind;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The business rules that turn what a person said into what is searched for (spec 013 research A1 step 2), in plain
 * Java so they are unit-tested without a model: "six people here tonight" means six or more local players on one
 * screen; "just me" means single player; "online with friends" means online multiplayer. Platform and place names that
 * do not exist in the library are dropped, never invented. The chips shown to the person come from the same list.
 */
@Component
public class ConstraintDeriver {

    private static final int MAX_GENRES = 5;

    public Derivation derive(QuestionInterpretation.Facts facts, LibraryVocabulary vocabulary) {
        List<AppliedConstraint> applied = new ArrayList<>();
        Integer partySize = facts.partySize();
        boolean groupPlay = partySize != null && partySize >= 2;
        boolean alone = !groupPlay && (Boolean.TRUE.equals(facts.playingAlone()) || Integer.valueOf(1).equals(partySize));
        boolean online = Boolean.TRUE.equals(facts.online());

        if (groupPlay) {
            applied.add(AppliedConstraint.of(Kind.MIN_LOCAL_PLAYERS, String.valueOf(partySize)));
            applied.add(AppliedConstraint.of(Kind.LOCAL_MULTIPLAYER));
        }
        if (alone) {
            applied.add(AppliedConstraint.of(Kind.SINGLE_PLAYER));
        }
        if (online) {
            applied.add(AppliedConstraint.of(Kind.ONLINE));
        }
        List<String> platforms = knownPlatforms(facts.platforms(), vocabulary);
        platforms.forEach(platform -> applied.add(AppliedConstraint.of(Kind.PLATFORM, platform)));
        List<String> places = knownPlaces(facts.places(), vocabulary);
        places.forEach(place -> applied.add(AppliedConstraint.of(Kind.PLACE, place)));
        if (facts.installStatus() == InstallFilter.INSTALLED) {
            applied.add(AppliedConstraint.of(Kind.INSTALLED));
        } else if (facts.installStatus() == InstallFilter.NOT_INSTALLED) {
            applied.add(AppliedConstraint.of(Kind.NOT_INSTALLED));
        }
        List<String> genres = cleanGenres(facts.genres());
        genres.forEach(genre -> applied.add(AppliedConstraint.of(Kind.GENRE, genre)));
        Integer yearMin = facts.releaseYearMin();
        Integer yearMax = facts.releaseYearMax();
        if (yearMin != null && yearMax != null && yearMin > yearMax) {
            Integer swap = yearMin;
            yearMin = yearMax;
            yearMax = swap;
        }
        if (yearMin != null || yearMax != null) {
            applied.add(AppliedConstraint.of(Kind.YEARS, yearRange(yearMin, yearMax)));
        }
        if (facts.markFilter() != null) {
            applied.add(AppliedConstraint.of(Kind.MARK, facts.markFilter().name() + "." + facts.markScope().name()));
        }

        if (facts.likeMyLiked() && facts.markFilter() == null) {
            applied.add(AppliedConstraint.of(Kind.LIKE_MY_LIKED));
        }

        Constraints constraints = new Constraints(groupPlay ? partySize : null, groupPlay, alone, online, platforms,
                places, facts.installStatus(), genres, yearMin, yearMax, facts.markFilter(), facts.markScope(),
                facts.likeMyLiked(), blankToNull(facts.semanticQuery()));
        Integer largest = vocabulary.largestKnownGroup();
        boolean exceeds = groupPlay && largest != null && partySize > largest;

        return new Derivation(constraints, List.copyOf(applied), exceeds, largest);
    }

    private List<String> knownPlatforms(List<String> mentioned, LibraryVocabulary vocabulary) {
        Set<String> known = new LinkedHashSet<>();
        for (String name : mentioned) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String canonical = PlatformCatalog.canonical(name.trim());
            matching(vocabulary.platforms(), canonical).or(() -> matching(vocabulary.platforms(), name.trim()))
                    .ifPresent(known::add);
        }

        return List.copyOf(known);
    }

    private List<String> knownPlaces(List<String> mentioned, LibraryVocabulary vocabulary) {
        Set<String> known = new LinkedHashSet<>();
        for (String name : mentioned) {
            if (StringUtils.hasText(name)) {
                matching(vocabulary.places(), name.trim()).ifPresent(known::add);
            }
        }

        return List.copyOf(known);
    }

    private Optional<String> matching(List<String> vocabulary, String name) {
        return vocabulary.stream().filter(candidate -> candidate.equalsIgnoreCase(name)).findFirst();
    }

    private List<String> cleanGenres(List<String> genres) {
        return genres.stream()
                .filter(StringUtils::hasText)
                .map(genre -> genre.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .limit(MAX_GENRES)
                .toList();
    }

    private String yearRange(Integer yearMin, Integer yearMax) {
        if (yearMin != null && yearMax != null) {
            return yearMin.equals(yearMax) ? String.valueOf(yearMin) : yearMin + "-" + yearMax;
        }

        return yearMin != null ? yearMin + "+" : "-" + yearMax;
    }

    private String blankToNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }
}
