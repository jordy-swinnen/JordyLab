package dev.jordy.jordylab.gamecatalog.util;

import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The one-off data work of spec 013 migration 2 (research B3): canonicalise platform names, compute every game's
 * {@code title_key}, and merge the duplicates the old one-platform-per-game rule created. Plain JDBC on purpose: a
 * Flyway Java migration runs before the Spring context exists, and the normalisation code ({@link TitleKeys},
 * {@link PlatformCatalog}) is the very code the running application uses.
 *
 * <p>Two games are the same game when they share an IGDB id, or share a title key without a conflicting id of the same
 * kind (two different Steam app ids, or two different IGDB ids, always stay separate). The survivor of a merge is the
 * richest row (enriched, then with a real cover, then with a Steam id, then the oldest). Every merge is logged by title.
 */
@Slf4j
public class GameIdentityBackfill {

    private static final List<String> REAL_COVER_STATUSES = List.of("EXTERNAL_URL", "LOCAL_UPLOAD");
    private static final List<String> EMPTY_COVER_STATUSES = List.of("PENDING", "PLACEHOLDER", "LOCAL_FALLBACK_REQUESTED");

    /** What a run did, for the log and for tests. */
    public record Result(int platformsRenamed, int titleKeysSet, List<Merge> merges, int igdbIdsCleared) {
    }

    public record Merge(UUID survivorId, String survivorTitle, List<UUID> mergedIds, List<String> mergedTitles) {
    }

    private record GameRow(UUID id, String title, String titleKey, String steamAppId, String igdbGameId,
            String enrichmentStatus, String coverStatus, Timestamp createdAt) {
    }

    private static final class Cluster {
        private final List<GameRow> members = new ArrayList<>();
        private String steamAppId;
        private String igdbGameId;

        private void add(GameRow row) {
            members.add(row);
            steamAppId = steamAppId == null ? row.steamAppId() : steamAppId;
            igdbGameId = igdbGameId == null ? row.igdbGameId() : igdbGameId;
        }

        private boolean compatibleWith(String otherSteamAppId, String otherIgdbGameId) {
            return (steamAppId == null || otherSteamAppId == null || steamAppId.equals(otherSteamAppId))
                    && (igdbGameId == null || otherIgdbGameId == null || igdbGameId.equals(otherIgdbGameId));
        }

        private void absorb(Cluster other) {
            other.members.forEach(this::add);
        }
    }

    public Result run(Connection connection) throws SQLException {
        int platformsRenamed = canonicalisePlatforms(connection);
        int titleKeysSet = computeTitleKeys(connection);
        List<GameRow> games = loadGames(connection);
        IgdbClearing igdbClearing = new IgdbClearing();
        List<Cluster> clusters = clusterGames(games, igdbClearing);
        igdbClearing.apply(connection);
        List<Merge> merges = new ArrayList<>();
        for (Cluster cluster : clusters) {
            if (cluster.members.size() > 1) {
                merges.add(merge(connection, cluster));
            }
        }
        log.info("Game identity backfill: {} platform name(s) canonicalised, {} title key(s) set, {} merge(s), "
                + "{} IGDB id(s) cleared", platformsRenamed, titleKeysSet, merges.size(), igdbClearing.cleared.size());

        return new Result(platformsRenamed, titleKeysSet, merges, igdbClearing.cleared.size());
    }

    // ------------------------------------------------------------------ platforms and title keys

    private int canonicalisePlatforms(Connection connection) throws SQLException {
        int renamed = 0;
        for (String table : List.of("game_installation", "game", "console")) {
            renamed += canonicalisePlatformColumn(connection, table);
        }

        return renamed;
    }

    private int canonicalisePlatformColumn(Connection connection, String table) throws SQLException {
        Map<String, String> renames = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT DISTINCT platform FROM " + table
                        + " WHERE platform IS NOT NULL")) {
            while (resultSet.next()) {
                String current = resultSet.getString(1);
                String canonical = PlatformCatalog.canonical(current);
                if (!current.equals(canonical)) {
                    renames.put(current, canonical);
                }
            }
        }
        int rows = 0;
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + table
                + " SET platform = ? WHERE platform = ?")) {
            for (Map.Entry<String, String> rename : renames.entrySet()) {
                update.setString(1, rename.getValue());
                update.setString(2, rename.getKey());
                rows += update.executeUpdate();
            }
        }

        return rows;
    }

    private int computeTitleKeys(Connection connection) throws SQLException {
        List<Object[]> updates = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT id, title, title_key FROM game")) {
            while (resultSet.next()) {
                String title = resultSet.getString("title");
                String expected = TitleKeys.keyFor(title);
                if (!expected.equals(resultSet.getString("title_key"))) {
                    updates.add(new Object[] {expected, resultSet.getObject("id", UUID.class)});
                }
            }
        }
        try (PreparedStatement update = connection.prepareStatement("UPDATE game SET title_key = ? WHERE id = ?")) {
            for (Object[] values : updates) {
                update.setString(1, (String) values[0]);
                update.setObject(2, values[1]);
                update.addBatch();
            }
            update.executeBatch();
        }

        return updates.size();
    }

    // ------------------------------------------------------------------ clustering

    private List<GameRow> loadGames(Connection connection) throws SQLException {
        List<GameRow> games = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT id, title, title_key, steam_app_id, igdb_game_id, "
                        + "enrichment_status, cover_status, created_at FROM game ORDER BY created_at NULLS LAST, id")) {
            while (resultSet.next()) {
                games.add(new GameRow(resultSet.getObject("id", UUID.class), resultSet.getString("title"),
                        resultSet.getString("title_key"), resultSet.getString("steam_app_id"),
                        resultSet.getString("igdb_game_id"), resultSet.getString("enrichment_status"),
                        resultSet.getString("cover_status"), resultSet.getTimestamp("created_at")));
            }
        }

        return games;
    }

    /** IGDB ids that could not be kept because the same id belongs to two games with different Steam ids. */
    private static final class IgdbClearing {
        private final List<UUID> cleared = new ArrayList<>();

        private void apply(Connection connection) throws SQLException {
            try (PreparedStatement update = connection.prepareStatement("UPDATE game SET igdb_game_id = NULL WHERE id = ?")) {
                for (UUID id : cleared) {
                    update.setObject(1, id);
                    update.addBatch();
                    log.warn("Cleared IGDB id of game {}: the same IGDB id belongs to another game with a different Steam id",
                            id);
                }
                update.executeBatch();
            }
        }
    }

    private List<Cluster> clusterGames(List<GameRow> games, IgdbClearing igdbClearing) {
        List<Cluster> clusters = new ArrayList<>();
        Map<String, List<Cluster>> byIgdb = new HashMap<>();
        for (GameRow game : games) {
            String igdbGameId = game.igdbGameId();
            Cluster target = null;
            boolean clearIgdb = false;
            if (igdbGameId != null) {
                List<Cluster> sameIgdb = byIgdb.computeIfAbsent(igdbGameId, key -> new ArrayList<>());
                for (Cluster candidate : sameIgdb) {
                    if (candidate.compatibleWith(game.steamAppId(), igdbGameId)) {
                        target = candidate;
                        break;
                    }
                }
                clearIgdb = target == null && !sameIgdb.isEmpty();
            }
            if (target == null) {
                target = new Cluster();
                clusters.add(target);
                if (igdbGameId != null && !clearIgdb) {
                    byIgdb.get(igdbGameId).add(target);
                }
            }
            if (clearIgdb) {
                igdbClearing.cleared.add(game.id());
                target.add(withoutIgdb(game));
            } else {
                target.add(game);
            }
        }

        return mergeClustersWithTheSameTitleKey(clusters);
    }

    private GameRow withoutIgdb(GameRow game) {
        return new GameRow(game.id(), game.title(), game.titleKey(), game.steamAppId(), null, game.enrichmentStatus(),
                game.coverStatus(), game.createdAt());
    }

    private List<Cluster> mergeClustersWithTheSameTitleKey(List<Cluster> initial) {
        List<Cluster> clusters = new ArrayList<>(initial);
        boolean changed = true;
        while (changed) {
            changed = false;
            Map<String, List<Cluster>> byTitleKey = new LinkedHashMap<>();
            for (Cluster cluster : clusters) {
                for (String titleKey : cluster.members.stream().map(GameRow::titleKey).distinct().toList()) {
                    byTitleKey.computeIfAbsent(titleKey, key -> new ArrayList<>()).add(cluster);
                }
            }
            outer:
            for (List<Cluster> bucket : byTitleKey.values()) {
                for (int first = 0; first < bucket.size(); first++) {
                    for (int second = first + 1; second < bucket.size(); second++) {
                        Cluster left = bucket.get(first);
                        Cluster right = bucket.get(second);
                        if (left != right && left.compatibleWith(right.steamAppId, right.igdbGameId)) {
                            left.absorb(right);
                            clusters.remove(right);
                            changed = true;
                            break outer;
                        }
                    }
                }
            }
        }

        return clusters;
    }

    // ------------------------------------------------------------------ merging

    private Merge merge(Connection connection, Cluster cluster) throws SQLException {
        List<GameRow> ranked = new ArrayList<>(cluster.members);
        ranked.sort(Comparator.comparingInt(GameIdentityBackfill::richness).reversed()
                .thenComparing(GameRow::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(GameRow::id));
        GameRow survivor = ranked.get(0);
        List<GameRow> duplicates = ranked.subList(1, ranked.size());
        for (GameRow duplicate : duplicates) {
            mergeInto(connection, survivor, duplicate);
        }
        log.info("Merged {} duplicate game(s) into '{}' ({}): {}", duplicates.size(), survivor.title(), survivor.id(),
                duplicates.stream().map(row -> "'" + row.title() + "' (" + row.id() + ")").toList());

        return new Merge(survivor.id(), survivor.title(), duplicates.stream().map(GameRow::id).toList(),
                duplicates.stream().map(GameRow::title).toList());
    }

    private static int richness(GameRow row) {
        int score = 0;
        score += "ENRICHED".equals(row.enrichmentStatus()) ? 100 : 0;
        score += REAL_COVER_STATUSES.contains(row.coverStatus()) ? 10 : 0;
        score += row.steamAppId() != null ? 1 : 0;

        return score;
    }

    private void mergeInto(Connection connection, GameRow survivor, GameRow duplicate) throws SQLException {
        moveExternalIds(connection, survivor, duplicate);
        fillMissingFacts(connection, survivor.id(), duplicate.id());
        repointInstallations(connection, survivor.id(), duplicate.id());
        repointLibraryEntries(connection, survivor.id(), duplicate.id());
        repointConsoleEntries(connection, survivor.id(), duplicate.id());
        execute(connection, "DELETE FROM game WHERE id = ?", duplicate.id());
    }

    private void moveExternalIds(Connection connection, GameRow survivor, GameRow duplicate) throws SQLException {
        if (duplicate.steamAppId() != null) {
            execute(connection, "UPDATE game SET steam_app_id = NULL WHERE id = ?", duplicate.id());
            execute(connection, "UPDATE game SET steam_app_id = COALESCE(steam_app_id, ?) WHERE id = ?",
                    duplicate.steamAppId(), survivor.id());
        }
        if (duplicate.igdbGameId() != null) {
            execute(connection, "UPDATE game SET igdb_game_id = NULL WHERE id = ?", duplicate.id());
            execute(connection, "UPDATE game SET igdb_game_id = COALESCE(igdb_game_id, ?) WHERE id = ?",
                    duplicate.igdbGameId(), survivor.id());
        }
    }

    private void fillMissingFacts(Connection connection, UUID survivorId, UUID duplicateId) throws SQLException {
        String realCovers = "('EXTERNAL_URL','LOCAL_UPLOAD')";
        String emptyCovers = "('PENDING','PLACEHOLDER','LOCAL_FALLBACK_REQUESTED')";
        String sql = "UPDATE game s SET "
                + "genre = COALESCE(s.genre, d.genre), genres = COALESCE(s.genres, d.genres), "
                + "developer = COALESCE(s.developer, d.developer), publisher = COALESCE(s.publisher, d.publisher), "
                + "release_year = COALESCE(s.release_year, d.release_year), "
                + "max_local_players = COALESCE(s.max_local_players, d.max_local_players), "
                + "online_multiplayer = COALESCE(s.online_multiplayer, d.online_multiplayer), "
                + "single_player = COALESCE(s.single_player, d.single_player), "
                + "local_multiplayer = COALESCE(s.local_multiplayer, d.local_multiplayer), "
                + "split_screen = COALESCE(s.split_screen, d.split_screen), "
                + "multiplayer_source = CASE WHEN s.multiplayer_source = 'UNKNOWN' AND d.multiplayer_source <> 'UNKNOWN' "
                + "THEN d.multiplayer_source ELSE s.multiplayer_source END, "
                + "enrichment_status = CASE WHEN s.description IS NULL AND d.description IS NOT NULL "
                + "THEN d.enrichment_status ELSE s.enrichment_status END, "
                + "description = COALESCE(s.description, d.description), "
                + "cover_status = CASE WHEN s.cover_status IN " + emptyCovers + " AND d.cover_status IN " + realCovers
                + " THEN d.cover_status ELSE s.cover_status END, "
                + "cover_ref = CASE WHEN s.cover_status IN " + emptyCovers + " AND d.cover_status IN " + realCovers
                + " THEN d.cover_ref ELSE s.cover_ref END, "
                + "banner_status = CASE WHEN s.banner_status IN " + emptyCovers + " AND d.banner_status IN " + realCovers
                + " THEN d.banner_status ELSE s.banner_status END, "
                + "banner_ref = CASE WHEN s.banner_status IN " + emptyCovers + " AND d.banner_status IN " + realCovers
                + " THEN d.banner_ref ELSE s.banner_ref END "
                + "FROM game d WHERE s.id = ? AND d.id = ?";
        execute(connection, sql, survivorId, duplicateId);
    }

    private void repointInstallations(Connection connection, UUID survivorId, UUID duplicateId) throws SQLException {
        execute(connection, "UPDATE game_installation SET game_id = ? WHERE game_id = ?", survivorId, duplicateId);
    }

    /** Keeps one entry per library source: an active entry beats a removed one, otherwise the survivor's stays. */
    private void repointLibraryEntries(Connection connection, UUID survivorId, UUID duplicateId) throws SQLException {
        execute(connection, "UPDATE game_library_entry s SET removed_at = NULL, last_seen_at = d.last_seen_at "
                + "FROM game_library_entry d WHERE s.game_id = ? AND d.game_id = ? "
                + "AND s.library_source = d.library_source AND s.removed_at IS NOT NULL AND d.removed_at IS NULL",
                survivorId, duplicateId);
        execute(connection, "DELETE FROM game_library_entry d WHERE d.game_id = ? AND EXISTS ("
                + "SELECT 1 FROM game_library_entry s WHERE s.game_id = ? AND s.library_source = d.library_source)",
                duplicateId, survivorId);
        execute(connection, "UPDATE game_library_entry SET game_id = ? WHERE game_id = ?", survivorId, duplicateId);
    }

    private void repointConsoleEntries(Connection connection, UUID survivorId, UUID duplicateId) throws SQLException {
        execute(connection, "DELETE FROM console_game_entry d WHERE d.game_id = ? AND EXISTS ("
                + "SELECT 1 FROM console_game_entry s WHERE s.game_id = ? AND s.console_id = d.console_id)",
                duplicateId, survivorId);
        execute(connection, "UPDATE console_game_entry SET game_id = ? WHERE game_id = ?", survivorId, duplicateId);
    }

    private void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }
}
