package dev.jordy.jordylab.gamecatalog.service.libbot;

import java.util.List;

/**
 * What exists in the library right now: the platform names and place labels (hosts and consoles) that games are on, and
 * the largest group any game is known to support locally. Names a question mentions are matched against this and dropped
 * when they are not in it (never invented).
 */
public record LibraryVocabulary(List<String> platforms, List<String> places, Integer largestKnownGroup) {

    public LibraryVocabulary {
        platforms = platforms == null ? List.of() : List.copyOf(platforms);
        places = places == null ? List.of() : List.copyOf(places);
    }
}
