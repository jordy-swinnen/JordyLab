package dev.jordy.jordylab.gamecatalog.service.libbot;

import dev.jordy.jordylab.gamecatalog.LibBotProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Makes "every reference is named in the answer" true by construction (spec 013 FR-003, SC-004). A reference survives
 * only when it is one of the candidates LibBot was given, its title occurs in the answer text (case, accents and
 * punctuation ignored, whole words only, a longer title wins over a shorter one it contains), and, when the model
 * recommended specific games, the model recommended it. At most {@code maxReferences} are kept.
 */
@Component
@RequiredArgsConstructor
public class LibBotAnswerValidator {

    private final LibBotProperties properties;

    public List<Candidate> validReferences(LibBotAnswer answer, List<Candidate> candidates) {
        List<Candidate> named = namedInText(answer.text(), candidates);
        Set<UUID> recommended = new LinkedHashSet<>(answer.recommendedGameIds());
        List<Candidate> recommendedAndNamed = named.stream()
                .filter(candidate -> recommended.contains(candidate.gameId()))
                .toList();
        List<Candidate> chosen = recommendedAndNamed.isEmpty() ? named : recommendedAndNamed;

        return chosen.stream().limit(properties.maxReferences()).toList();
    }

    /** Candidates whose title appears in the text, in order of first appearance. */
    private List<Candidate> namedInText(String text, List<Candidate> candidates) {
        StringBuilder haystack = new StringBuilder(" " + normalise(text) + " ");
        List<Candidate> longestFirst = candidates.stream()
                .sorted(Comparator.comparingInt((Candidate candidate) -> normalise(candidate.title()).length()).reversed())
                .toList();
        List<Match> matches = new ArrayList<>();
        for (Candidate candidate : longestFirst) {
            String needle = " " + normalise(candidate.title()) + " ";
            if (needle.trim().isEmpty()) {
                continue;
            }
            int index = haystack.indexOf(needle);
            if (index < 0) {
                continue;
            }
            matches.add(new Match(index, candidate));
            blankOut(haystack, needle);
        }

        return matches.stream().sorted(Comparator.comparingInt(Match::index)).map(Match::candidate).toList();
    }

    /** Replaces every occurrence with filler so a shorter title cannot match inside an already matched longer one. */
    private void blankOut(StringBuilder haystack, String needle) {
        String filler = "\u0001".repeat(needle.length() - 2);
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            haystack.replace(index + 1, index + needle.length() - 1, filler);
            index = haystack.indexOf(needle);
        }
    }

    static String normalise(String text) {
        String decomposed = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private record Match(int index, Candidate candidate) {
    }
}
