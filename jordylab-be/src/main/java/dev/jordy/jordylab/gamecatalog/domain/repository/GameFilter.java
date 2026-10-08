package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

/**
 * What the library grid asks for (spec 013 contracts/catalog-api.md). Every list is "any of"; an empty list means the
 * filter is off. {@code installStatus} is {@code INSTALLED}, {@code NOT_INSTALLED} or {@code ALL}. {@code userSubject} is
 * the asker, needed only for "my marks".
 */
@Builder(toBuilder = true)
public record GameFilter(
        String search,
        List<String> platforms,
        List<UUID> whereIds,
        String installStatus,
        List<GameSource> sources,
        Integer minLocalPlayers,
        List<RomStatus> romStatuses,
        List<MarkType> marks,
        MarkScope markScope,
        String userSubject,
        Sort sort) {

    public enum Sort {
        TITLE,
        MOST_WANTED,
        MOST_LIKED
    }

    /** Whose marks a mark filter looks at. */
    public enum MarkScope {
        ALL,
        MINE
    }

    public GameFilter {
        platforms = platforms == null ? List.of() : List.copyOf(platforms);
        whereIds = whereIds == null ? List.of() : List.copyOf(whereIds);
        sources = sources == null ? List.of() : List.copyOf(sources);
        romStatuses = romStatuses == null ? List.of() : List.copyOf(romStatuses);
        marks = marks == null ? List.of() : List.copyOf(marks);
        installStatus = installStatus == null ? "INSTALLED" : installStatus;
        markScope = markScope == null ? MarkScope.ALL : markScope;
        sort = sort == null ? Sort.TITLE : sort;
    }

    public static GameFilter everything() {
        return GameFilter.builder().installStatus("ALL").build();
    }
}
