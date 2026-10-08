package dev.jordy.jordylab.gamecatalog.util;

import org.assertj.core.api.SoftAssertions;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migrations 1 to 3 of spec 013 against a real PostgreSQL: the old schema is built by the earlier migrations, rows are
 * created in test code only (never in a live database), then the Java migration merges duplicates and migration 3
 * tightens the schema. Every test gets its own database.
 */
@Testcontainers
class GameIdentityBackfillTest {

    private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();
    private static final String AFTER_PLACES_MIGRATION = "20261008001";
    private static final String BEFORE_PLACES_MIGRATION = "20261005001";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

    private String databaseUrl;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createAnEmptyDatabase() {
        String database = "backfill_" + DATABASE_COUNTER.incrementAndGet();
        new JdbcTemplate(adminDataSource()).execute("CREATE DATABASE " + database);
        databaseUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + database;
        jdbc = new JdbcTemplate(new DriverManagerDataSource(databaseUrl + "?currentSchema=gamecatalog,public", postgres.getUsername(), postgres.getPassword()));
    }

    @AfterEach
    void clearTheDryRunSwitch() {
        System.clearProperty("jordylab.migration.dry-run");
    }

    @Test
    void mergesTheSameTitleSplitAcrossPlatformsAndMovesEveryPlace() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        UUID host = insertHost("htpc");
        UUID emulatorSource = insertScanSource(host, "EMUDECK");
        UUID steamGame = insertGame("Hades", "Steam", "1145360", null, "ENRICHED", "EXTERNAL_URL");
        UUID switchGame = insertGame("hades", "Nintendo Switch", null, null, "PENDING", "PLACEHOLDER");
        UUID romGame = insertGame("HADES (USA)", "SNES", null, null, "PENDING", "PENDING");
        UUID console = insertConsole("Nintendo Switch", "Nintendo Switch");
        insertConsoleEntry(switchGame, console);
        insertInstallation(romGame, emulatorSource, "SNES", "roms/snes/Hades (USA).smc");

        flyway(null).migrate();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(count("game")).isEqualTo(1);
            softly.assertThat(jdbc.queryForObject("SELECT id FROM game", UUID.class)).isEqualTo(steamGame);
            softly.assertThat(jdbc.queryForObject("SELECT game_id FROM game_installation", UUID.class)).isEqualTo(steamGame);
            softly.assertThat(jdbc.queryForObject("SELECT game_id FROM console_game_entry", UUID.class)).isEqualTo(steamGame);
            softly.assertThat(jdbc.queryForObject("SELECT title_key FROM game", String.class)).isEqualTo("hades");
        });
    }

    @Test
    void keepsTheRichestRowAsSurvivorAndFillsWhatItLacks() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        UUID richer = insertGame("Celeste", "Steam", "504230", null, "ENRICHED", "EXTERNAL_URL");
        UUID poorer = insertGame("Celeste", "Nintendo Switch", null, null, "PENDING", "PENDING");
        jdbc.update("UPDATE game SET genre = 'Platformer', developer = 'Maddy Makes Games' WHERE id = ?", poorer);

        flyway(null).migrate();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(jdbc.queryForObject("SELECT id FROM game", UUID.class)).isEqualTo(richer);
            softly.assertThat(jdbc.queryForObject("SELECT genre FROM game", String.class)).isEqualTo("Platformer");
            softly.assertThat(jdbc.queryForObject("SELECT developer FROM game", String.class))
                    .isEqualTo("Maddy Makes Games");
            softly.assertThat(jdbc.queryForObject("SELECT cover_status FROM game", String.class)).isEqualTo("EXTERNAL_URL");
        });
    }

    @Test
    void keepsOneLibraryEntryPerSourceAndPrefersTheActiveOne() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        UUID survivor = insertGame("Portal 2", "Steam", "620", null, "ENRICHED", "EXTERNAL_URL");
        UUID duplicate = insertGame("Portal 2", "Nintendo Switch", null, null, "PENDING", "PENDING");
        insertLibraryEntry(survivor, "OWNED", true);
        insertLibraryEntry(duplicate, "OWNED", false);
        insertLibraryEntry(duplicate, "FAMILY", false);

        flyway(null).migrate();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(count("game")).isEqualTo(1);
            softly.assertThat(count("game_library_entry")).isEqualTo(2);
            softly.assertThat(jdbc.queryForObject(
                    "SELECT removed_at IS NULL FROM game_library_entry WHERE library_source = 'OWNED'", Boolean.class))
                    .isTrue();
            softly.assertThat(jdbc.queryForObject("SELECT DISTINCT game_id FROM game_library_entry", UUID.class))
                    .isEqualTo(survivor);
        });
    }

    @Test
    void anActiveEntryOnTheDuplicateRestoresARemovedEntryOnTheSurvivor() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        UUID survivor = insertGame("Portal 2", "Steam", "620", null, "ENRICHED", "EXTERNAL_URL");
        UUID duplicate = insertGame("Portal 2", "Nintendo Switch", null, null, "PENDING", "PENDING");
        insertLibraryEntry(survivor, "OWNED", false);
        insertLibraryEntry(duplicate, "OWNED", true);

        flyway(null).migrate();

        assertThat(jdbc.queryForObject("SELECT removed_at IS NULL FROM game_library_entry", Boolean.class)).isTrue();
    }

    @Test
    void dropsTheSecondConsoleEntryWhenBothMergedGamesWereOnTheSameConsole() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        UUID first = insertGame("Mario Kart 8 Deluxe", "Nintendo Switch", null, "13427", "PENDING", "PENDING");
        UUID second = insertGame("Mario Kart 8 Deluxe (Europe)", "Nintendo Switch", null, null, "PENDING", "PENDING");
        UUID console = insertConsole("Nintendo Switch", "Nintendo Switch");
        insertConsoleEntry(first, console);
        insertConsoleEntry(second, console);

        flyway(null).migrate();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(count("game")).isEqualTo(1);
            softly.assertThat(count("console_game_entry")).isEqualTo(1);
        });
    }

    @Test
    void mergesGamesSharingAnIgdbIdEvenWhenTheirTitlesDiffer() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        insertGame("Super Mario All-Stars", "SNES", null, "9999", "PENDING", "PENDING");
        insertGame("Super Mario All-Stars: 25th Anniversary", "Wii", null, "9999", "PENDING", "PENDING");

        flyway(null).migrate();

        assertThat(count("game")).isEqualTo(1);
    }

    @Test
    void keepsGamesWithDifferentSteamIdsSeparateEvenWithTheSameTitle() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        insertGame("Tomb Raider", "Steam", "203160", null, "PENDING", "PENDING");
        insertGame("Tomb Raider", "Steam", "224960", null, "PENDING", "PENDING");

        flyway(null).migrate();

        assertThat(count("game")).isEqualTo(2);
    }

    @Test
    void keepsGamesWithDifferentIgdbIdsSeparateEvenWithTheSameTitle() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        insertGame("Resident Evil 4", "GameCube", null, "111", "PENDING", "PENDING");
        insertGame("Resident Evil 4", "PlayStation 5", null, "222", "PENDING", "PENDING");

        flyway(null).migrate();

        assertThat(count("game")).isEqualTo(2);
    }

    @Test
    void canonicalisesPlatformNamesOnInstallationsAndConsoles() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        UUID host = insertHost("htpc");
        UUID source = insertScanSource(host, "EMUDECK");
        UUID game = insertGame("Super Mario 64", "N64", null, null, "PENDING", "PENDING");
        insertInstallation(game, source, "N64", "roms/n64/Super Mario 64.z64");
        insertConsole("PS5", "My PlayStation");

        flyway(null).migrate();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(jdbc.queryForObject("SELECT platform FROM game_installation", String.class))
                    .isEqualTo("Nintendo 64");
            softly.assertThat(jdbc.queryForObject("SELECT platform FROM console", String.class))
                    .isEqualTo("PlayStation 5");
        });
    }

    @Test
    void theSwitchVirtualSourceBecomesAConsoleWithItsGamesAndLosesItsOwnedLibraryEntries() {
        flyway(BEFORE_PLACES_MIGRATION).migrate();
        UUID virtualSource = jdbc.queryForObject("SELECT id FROM scan_source WHERE source_type = 'SWITCH'", UUID.class);
        UUID game = insertGame("Mario Kart 8 Deluxe", "Nintendo Switch", null, null, "PENDING", "PENDING");
        insertOldInstallation(game, virtualSource, "mk8d", true, "PHYSICAL");
        insertLibraryEntry(game, "OWNED", true);

        flyway(null).migrate();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(jdbc.queryForObject("SELECT name FROM console", String.class)).isEqualTo("Nintendo Switch");
            softly.assertThat(count("console_game_entry")).isEqualTo(1);
            softly.assertThat(count("game_installation")).isZero();
            softly.assertThat(count("game_library_entry")).isZero();
            softly.assertThat(jdbc.queryForObject("SELECT count(*) FROM scan_source WHERE source_type = 'SWITCH'",
                    Integer.class)).isZero();
        });
    }

    @Test
    void anEmptyVirtualSwitchSourceDoesNotCreateAConsole() {
        flyway(BEFORE_PLACES_MIGRATION).migrate();
        assertThat(count("scan_source")).isEqualTo(1);

        flyway(null).migrate();

        assertThat(count("console")).isZero();
    }

    @Test
    void hostsAreCreatedFromScanSourceHostnamesIgnoringCase() {
        flyway(BEFORE_PLACES_MIGRATION).migrate();
        insertOldScanSource("CachyOS-HTPC", "STEAM");
        insertOldScanSource("cachyos-htpc", "EMUDECK");
        insertOldScanSource("MacBookPro", "STEAM");

        flyway(null).migrate();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(jdbc.queryForList("SELECT lower(hostname) FROM host ORDER BY 1", String.class))
                    .containsExactly("cachyos-htpc", "macbookpro");
            softly.assertThat(jdbc.queryForObject("""
                    SELECT count(DISTINCT host_id) FROM scan_source WHERE source_key LIKE '%cachyos-htpc%'
                       OR source_key LIKE '%CachyOS-HTPC%'
                    """, Integer.class)).isEqualTo(1);
        });
    }

    @Test
    void emulatedCopiesStartWithAnUnknownRomStatusAndSteamCopiesHaveNone() {
        flyway(BEFORE_PLACES_MIGRATION).migrate();
        UUID emulator = insertOldScanSource("htpc", "EMUDECK");
        UUID steam = insertOldScanSource("htpc", "STEAM");
        UUID rom = insertGame("Super Mario World", "SNES", null, null, "PENDING", "PENDING");
        UUID steamGame = insertGame("Hades", "Steam", "1145360", null, "PENDING", "PENDING");
        insertOldInstallation(rom, emulator, "roms/snes/smw.smc", false, null);
        insertOldInstallation(steamGame, steam, "1145360", false, null);

        flyway(null).migrate();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(jdbc.queryForObject("SELECT rom_status FROM game_installation WHERE external_ref = 'roms/snes/smw.smc'",
                    String.class)).isEqualTo("UNKNOWN");
            softly.assertThat(jdbc.queryForObject("SELECT rom_status FROM game_installation WHERE external_ref = '1145360'",
                    String.class)).isNull();
            softly.assertThat(jdbc.queryForObject("SELECT platform FROM game_installation WHERE external_ref = 'roms/snes/smw.smc'",
                    String.class)).isEqualTo("SNES");
        });
    }

    @Test
    void descriptionProvenanceIsBackfilledFromTheEnrichmentStatus() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        UUID aiGame = insertGame("Aiwritten", "SNES", null, null, "ENRICHED", "PENDING");
        UUID steamGame = insertGame("Steamwritten", "Steam", "1", null, "PENDING", "PENDING");
        jdbc.update("UPDATE game SET description = 'Text' WHERE id IN (?, ?)", aiGame, steamGame);

        flyway(null).migrate();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(jdbc.queryForObject("SELECT description_source FROM game WHERE id = ?", String.class, aiGame))
                    .isEqualTo("AI");
            softly.assertThat(jdbc.queryForObject("SELECT description_model FROM game WHERE id = ?", String.class, aiGame))
                    .isNull();
            softly.assertThat(jdbc.queryForObject("SELECT description_source FROM game WHERE id = ?", String.class, steamGame))
                    .isEqualTo("STEAM");
        });
    }

    @Test
    void afterAllThreeMigrationsTheSchemaIsTheNewOne() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        insertGame("Hades", "Steam", "1145360", null, "PENDING", "PENDING");

        flyway(null).migrate();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(columnExists("game", "platform")).isFalse();
            softly.assertThat(columnExists("game", "title_key")).isTrue();
            softly.assertThat(columnExists("game_installation", "manual")).isFalse();
            softly.assertThat(columnExists("game_installation", "format")).isFalse();
            softly.assertThat(columnExists("scan_source", "hostname")).isFalse();
            softly.assertThat(columnExists("scan_source", "host_id")).isTrue();
            softly.assertThat(count("game_mark")).isZero();
            softly.assertThat(count("refresh_run")).isZero();
            softly.assertThat(count("game_embedding")).isZero();
        });
    }

    @Test
    void aMarkIsUniquePerUserAndGameAndOnlyOneRefreshRunPerKindCanRun() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        UUID game = insertGame("Hades", "Steam", "1145360", null, "PENDING", "PENDING");
        flyway(null).migrate();
        jdbc.update("INSERT INTO game_mark (id, game_id, user_subject, mark) VALUES (gen_random_uuid(), ?, 'u1', 'WANT_TO_PLAY')",
                game);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO game_mark (id, game_id, user_subject, mark) VALUES (gen_random_uuid(), ?, 'u1', 'PLAYED_LIKED')",
                game)).hasMessageContaining("uq_game_mark_game_user");

        jdbc.update("""
                INSERT INTO refresh_run (id, kind, status, started_by, started_at)
                VALUES (gen_random_uuid(), 'DATA', 'RUNNING', 'admin', now())
                """);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO refresh_run (id, kind, status, started_by, started_at)
                VALUES (gen_random_uuid(), 'DATA', 'RUNNING', 'admin', now())
                """)).hasMessageContaining("uq_refresh_run_running_per_kind");
        jdbc.update("""
                INSERT INTO refresh_run (id, kind, status, started_by, started_at)
                VALUES (gen_random_uuid(), 'AI', 'RUNNING', 'admin', now())
                """);
    }

    @Test
    void theVectorColumnAcceptsAnEmbeddingAndOrdersByCosineDistance() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        UUID near = insertGame("Near", "Steam", "10", null, "PENDING", "PENDING");
        UUID far = insertGame("Far", "Steam", "11", null, "PENDING", "PENDING");
        flyway(null).migrate();
        String nearVector = vectorLiteral(1.0f, 0.0f);
        String farVector = vectorLiteral(0.0f, 1.0f);
        jdbc.update("""
                INSERT INTO game_embedding (game_id, model, content_hash, embedding, embedded_at)
                VALUES (?, 'm', 'h', CAST(? AS vector), now())
                """, near, nearVector.replace("]", paddedZeros() + "]"));
        jdbc.update("""
                INSERT INTO game_embedding (game_id, model, content_hash, embedding, embedded_at)
                VALUES (?, 'm', 'h', CAST(? AS vector), now())
                """, far, farVector.replace("]", paddedZeros() + "]"));

        List<UUID> ordered = jdbc.queryForList("""
                SELECT game_id FROM game_embedding ORDER BY embedding <=> CAST(? AS vector) LIMIT 2
                """, UUID.class, nearVector.replace("]", paddedZeros() + "]"));

        assertThat(ordered).containsExactly(near, far);
    }

    @Test
    void aDryRunLogsThePlanThenRollsEverythingBack() {
        flyway(AFTER_PLACES_MIGRATION).migrate();
        insertGame("Hades", "Steam", "1145360", null, "ENRICHED", "EXTERNAL_URL");
        insertGame("hades", "Nintendo Switch", null, null, "PENDING", "PENDING");
        System.setProperty("jordylab.migration.dry-run", "true");

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .rootCause()
                .hasMessageContaining("Dry run complete");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(count("game")).isEqualTo(2);
            softly.assertThat(jdbc.queryForObject("SELECT count(*) FROM game WHERE title_key IS NOT NULL", Integer.class))
                    .isZero();
        });
    }

    // ------------------------------------------------------------------ helpers

    private Flyway flyway(String target) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(databaseUrl, postgres.getUsername(), postgres.getPassword());
        if (target != null) {
            configuration.target(target);
        }

        return configuration.load();
    }

    private DriverManagerDataSource adminDataSource() {
        return new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private boolean columnExists(String table, String column) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'gamecatalog' AND table_name = ? AND column_name = ?
                """, Integer.class, table, column) > 0;
    }

    private UUID insertHost(String hostname) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO host (id, hostname) VALUES (?, ?)", id, hostname);

        return id;
    }

    private UUID insertScanSource(UUID hostId, String sourceType) {
        UUID id = UUID.randomUUID();
        String hostname = jdbc.queryForObject("SELECT hostname FROM host WHERE id = ?", String.class, hostId);
        jdbc.update("""
                INSERT INTO scan_source (id, source_key, source_type, platform, enabled, host_id)
                VALUES (?, ?, ?, ?, true, ?)
                """, id, hostname + ":" + sourceType, sourceType, "STEAM".equals(sourceType) ? "Steam" : "EmuDeck",
                hostId);

        return id;
    }

    private UUID insertOldScanSource(String hostname, String sourceType) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO scan_source (id, source_key, source_type, platform, enabled, hostname)
                VALUES (?, ?, ?, ?, true, ?)
                """, id, hostname + ":" + sourceType, sourceType, "STEAM".equals(sourceType) ? "Steam" : "EmuDeck", hostname);

        return id;
    }

    private void insertOldInstallation(UUID gameId, UUID sourceId, String externalRef, boolean manual, String format) {
        jdbc.update("""
                INSERT INTO game_installation (id, game_id, source_id, external_ref, presence, manual, format,
                                               first_seen_at, last_seen_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'INSTALLED', ?, ?, now(), now())
                """, gameId, sourceId, externalRef, manual, format);
    }

    private UUID insertGame(String title, String platform, String steamAppId, String igdbGameId,
            String enrichmentStatus, String coverStatus) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO game (id, platform, title, steam_app_id, igdb_game_id, enrichment_status, cover_status,
                                  created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now() + (? * interval '1 second'), now())
                """, id, platform, title, steamAppId, igdbGameId, enrichmentStatus, coverStatus, DATABASE_COUNTER.incrementAndGet());

        return id;
    }

    private void insertInstallation(UUID gameId, UUID sourceId, String platform, String externalRef) {
        jdbc.update("""
                INSERT INTO game_installation (id, game_id, source_id, external_ref, platform, presence, first_seen_at,
                                               last_seen_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, 'INSTALLED', now(), now())
                """, gameId, sourceId, externalRef, platform);
    }

    private void insertLibraryEntry(UUID gameId, String librarySource, boolean active) {
        jdbc.update("""
                INSERT INTO game_library_entry (id, game_id, library_source, first_seen_at, last_seen_at, removed_at)
                VALUES (gen_random_uuid(), ?, ?, now(), now(), CASE WHEN ? THEN NULL ELSE now() END)
                """, gameId, librarySource, active);
    }

    private UUID insertConsole(String platform, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO console (id, platform, name) VALUES (?, ?, ?)", id, platform, name);

        return id;
    }

    private void insertConsoleEntry(UUID gameId, UUID consoleId) {
        jdbc.update("INSERT INTO console_game_entry (id, game_id, console_id) VALUES (gen_random_uuid(), ?, ?)", gameId,
                consoleId);
    }

    private static String vectorLiteral(float first, float second) {
        return "[" + first + "," + second + "]";
    }

    private static String paddedZeros() {
        return ",0".repeat(1534);
    }
}
