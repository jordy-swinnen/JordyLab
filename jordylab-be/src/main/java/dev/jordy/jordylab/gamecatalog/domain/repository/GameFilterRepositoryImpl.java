package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.jordy.jordylab.gamecatalog.domain.repository.GameVisibility.ACTIVE_LIBRARY_ENTRY;
import static dev.jordy.jordylab.gamecatalog.domain.repository.GameVisibility.AVAILABLE_NOW;
import static dev.jordy.jordylab.gamecatalog.domain.repository.GameVisibility.VISIBLE;

/**
 * Builds the grid query from the active filters only, on top of the one visibility rule. Values are always bound
 * parameters, never concatenated, and the order is chosen from a fixed set, so nothing a client sends reaches the query text.
 */
class GameFilterRepositoryImpl implements GameFilterRepository {

    private static final String OWNED_ENTRY = "EXISTS (SELECT 1 FROM GameLibraryEntry so WHERE so.game = g "
            + "AND so.removedAt IS NULL AND so.librarySource = 'OWNED')";
    private static final String INSTALLED_STEAM_COPY = "EXISTS (SELECT 1 FROM GameInstallation sgi WHERE sgi.game = g "
            + "AND sgi.presence = 'INSTALLED' AND sgi.source.enabled = true AND sgi.source.sourceType = 'STEAM')";
    private static final String FAMILY_ENTRY = "EXISTS (SELECT 1 FROM GameLibraryEntry sf WHERE sf.game = g "
            + "AND sf.removedAt IS NULL AND sf.librarySource = 'FAMILY')";
    private static final String UNKNOWN_PLAYER_COUNT = "g.maxLocalPlayers IS NULL "
            + "AND (g.localMultiplayer IS NULL OR g.localMultiplayer = true)";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Game> findFiltered(GameFilter filter, Pageable pageable) {
        Clauses clauses = clauses(filter, true);
        TypedQuery<Long> count = entityManager.createQuery(
                "SELECT COUNT(g) FROM Game g WHERE " + clauses.where(), Long.class);
        clauses.parameters().forEach(count::setParameter);
        long total = count.getSingleResult();
        TypedQuery<Game> query = entityManager.createQuery(
                "SELECT g FROM Game g WHERE " + clauses.where() + " ORDER BY " + orderBy(filter.sort()), Game.class);
        clauses.parameters().forEach(query::setParameter);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(query.getResultList(), pageable, total);
    }

    @Override
    public long countWithUnknownPlayerCount(GameFilter filter) {
        Clauses clauses = clauses(filter, false);
        TypedQuery<Long> count = entityManager.createQuery(
                "SELECT COUNT(g) FROM Game g WHERE " + clauses.where() + " AND " + UNKNOWN_PLAYER_COUNT, Long.class);
        clauses.parameters().forEach(count::setParameter);

        return count.getSingleResult();
    }

    private String orderBy(GameFilter.Sort sort) {
        return switch (sort) {
            case TITLE -> "LOWER(g.title)";
            case MOST_WANTED -> "(SELECT COUNT(wm) FROM GameMark wm WHERE wm.game = g AND wm.mark = 'WANT_TO_PLAY') DESC, "
                    + "LOWER(g.title)";
            case MOST_LIKED -> "(SELECT COUNT(lm) FROM GameMark lm WHERE lm.game = g AND lm.mark = 'PLAYED_LIKED') DESC, "
                    + "LOWER(g.title)";
        };
    }

    private Clauses clauses(GameFilter filter, boolean includePlayerCount) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();
        conditions.add(VISIBLE);
        if (StringUtils.hasText(filter.search())) {
            conditions.add("LOWER(g.title) LIKE :search");
            parameters.put("search", "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%");
        }
        installStatus(filter.installStatus(), conditions);
        if (!filter.platforms().isEmpty()) {
            conditions.add("(EXISTS (SELECT 1 FROM GameInstallation pgi WHERE pgi.game = g AND pgi.platform IN :platforms "
                    + "AND pgi.presence = 'INSTALLED' AND pgi.source.enabled = true) "
                    + "OR ('Steam' IN :platforms AND " + ACTIVE_LIBRARY_ENTRY + ") "
                    + "OR EXISTS (SELECT 1 FROM ConsoleGameEntry pce WHERE pce.game = g "
                    + "AND pce.console.platform IN :platforms))");
            parameters.put("platforms", filter.platforms());
        }
        if (!filter.whereIds().isEmpty()) {
            conditions.add("(EXISTS (SELECT 1 FROM GameInstallation wgi WHERE wgi.game = g AND wgi.presence = 'INSTALLED' "
                    + "AND wgi.source.enabled = true AND wgi.source.host.id IN :whereIds) "
                    + "OR EXISTS (SELECT 1 FROM ConsoleGameEntry wce WHERE wce.game = g AND wce.console.id IN :whereIds))");
            parameters.put("whereIds", filter.whereIds());
        }
        if (!filter.sources().isEmpty()) {
            conditions.add("(" + String.join(" OR ", filter.sources().stream().map(this::sourceCondition).toList()) + ")");
        }
        if (includePlayerCount && filter.minLocalPlayers() != null) {
            conditions.add("g.maxLocalPlayers >= :minLocalPlayers");
            parameters.put("minLocalPlayers", filter.minLocalPlayers());
        }
        if (!filter.romStatuses().isEmpty()) {
            conditions.add("EXISTS (SELECT 1 FROM GameInstallation rgi WHERE rgi.game = g AND rgi.presence = 'INSTALLED' "
                    + "AND rgi.source.enabled = true AND rgi.source.sourceType = 'EMUDECK' "
                    + "AND rgi.romStatus IN :romStatuses)");
            parameters.put("romStatuses", filter.romStatuses());
        }
        if (!filter.marks().isEmpty()) {
            boolean mine = filter.markScope() == GameFilter.MarkScope.MINE;
            conditions.add("EXISTS (SELECT 1 FROM GameMark fm WHERE fm.game = g AND fm.mark IN :marks"
                    + (mine ? " AND fm.userSubject = :userSubject" : "") + ")");
            parameters.put("marks", filter.marks());
            if (mine) {
                parameters.put("userSubject", filter.userSubject() == null ? "" : filter.userSubject());
            }
        }

        return new Clauses(String.join(" AND ", conditions), parameters);
    }

    private void installStatus(String installStatus, List<String> conditions) {
        if ("INSTALLED".equals(installStatus)) {
            conditions.add(AVAILABLE_NOW);
        } else if ("NOT_INSTALLED".equals(installStatus)) {
            conditions.add("NOT " + AVAILABLE_NOW);
        }
    }

    private String sourceCondition(GameSource source) {
        return switch (source) {
            case STEAM_OWNED -> "(" + OWNED_ENTRY + " OR (" + INSTALLED_STEAM_COPY + " AND NOT " + ACTIVE_LIBRARY_ENTRY + "))";
            case STEAM_FAMILY -> "(" + FAMILY_ENTRY + " AND NOT " + OWNED_ENTRY + ")";
            case EMULATED -> "EXISTS (SELECT 1 FROM GameInstallation egi WHERE egi.game = g AND egi.presence = 'INSTALLED' "
                    + "AND egi.source.enabled = true AND egi.source.sourceType = 'EMUDECK')";
            case CONSOLE -> "EXISTS (SELECT 1 FROM ConsoleGameEntry sce WHERE sce.game = g)";
        };
    }

    private record Clauses(String where, Map<String, Object> parameters) {
    }
}
