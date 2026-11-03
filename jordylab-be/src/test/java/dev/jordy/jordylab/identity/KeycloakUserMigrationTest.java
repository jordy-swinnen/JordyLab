package dev.jordy.jordylab.identity;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * The release-A cutover migration (spec 019 US7, D10) against a real PostgreSQL and a synthetic Keycloak 26 schema: persons
 * are copied with their subject as the id, usernames lowercased, admin &gt; guest roles, the {@code app-gamecatalog} grant,
 * no password, and service accounts skipped. Databases without the schema migrate as a no-op.
 */
@Testcontainers
class KeycloakUserMigrationTest {

    private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();
    private static final String JORDYLAB_REALM = "11111111-1111-4111-8111-111111111111";
    private static final String MASTER_REALM = "22222222-2222-4222-8222-222222222222";
    private static final String ADMIN_ROLE = "33333333-3333-4333-8333-333333333333";
    private static final String GUEST_ROLE = "44444444-4444-4444-8444-444444444444";
    private static final String APP_GAME_CATALOG_ROLE = "55555555-5555-4555-8555-555555555555";
    private static final String MASTER_ADMIN_ROLE = "66666666-6666-4666-8666-666666666666";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

    private String databaseUrl;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createAnEmptyDatabase() {
        String database = "cutover_" + DATABASE_COUNTER.incrementAndGet();
        new JdbcTemplate(adminDataSource()).execute("CREATE DATABASE " + database);
        databaseUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + database;
        jdbc = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, postgres.getUsername(), postgres.getPassword()));
    }

    @Test
    void copiesEveryPersonWithIdentityRoleAndGrant() {
        createKeycloakSchema();

        flyway().migrate();

        List<Map<String, Object>> accounts = jdbc.queryForList("""
                SELECT id, username, display_name, password_hash, role, status
                FROM identity.user_account ORDER BY username""");
        assertThat(accounts).hasSize(3);
        assertSoftly(softly -> {
            softly.assertThat(accounts.get(0))
                    .containsEntry("username", "guest.person")
                    .containsEntry("display_name", "Ada Lovelace")
                    .containsEntry("role", "GUEST")
                    .containsEntry("status", "APPROVED");
            softly.assertThat(accounts.get(0).get("id").toString())
                    .isEqualTo("aaaaaaaa-1111-4111-8111-111111111111");
            softly.assertThat(accounts.get(0).get("password_hash")).isNull();
            softly.assertThat(accounts.get(1))
                    .containsEntry("username", "jordy")
                    .containsEntry("display_name", "Jordy Swinnen")
                    .containsEntry("role", "ADMIN");
            softly.assertThat(accounts.get(2))
                    .containsEntry("username", "plain")
                    .containsEntry("role", "GUEST");
            softly.assertThat(accounts.get(2).get("display_name")).isNull();
        });

        List<Map<String, Object>> grants = jdbc.queryForList("""
                SELECT account.username, app_grant.app
                FROM identity.user_app_grant app_grant
                         JOIN identity.user_account account ON account.id = app_grant.user_account_id
                ORDER BY account.username""");
        assertThat(grants).extracting(row -> row.get("username")).containsExactly("guest.person", "jordy");
        assertThat(grants).allSatisfy(row -> assertThat(row.get("app")).isEqualTo("GAME_CATALOG"));
    }

    @Test
    void skipsServiceAccountsAndOtherRealms() {
        createKeycloakSchema();

        flyway().migrate();

        assertThat(jdbc.queryForList("SELECT username FROM identity.user_account", String.class))
                .doesNotContain("service-account-scan", "master-admin");
    }

    @Test
    void isANoOpWithoutTheKeycloakSchema() {
        flyway().migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.user_account", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.user_app_grant", Integer.class)).isZero();
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(databaseUrl, postgres.getUsername(), postgres.getPassword())
                .load();
    }

    private DriverManagerDataSource adminDataSource() {
        return new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    /** The subset of Keycloak 26's schema the cutover reads, plus two persons, one service account and one master-realm admin. */
    private void createKeycloakSchema() {
        jdbc.execute("CREATE SCHEMA keycloak");
        jdbc.execute("""
                CREATE TABLE keycloak.realm (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255))""");
        jdbc.execute("""
                CREATE TABLE keycloak.user_entity (
                    id VARCHAR(255) PRIMARY KEY,
                    username VARCHAR(255),
                    first_name VARCHAR(255),
                    last_name VARCHAR(255),
                    realm_id VARCHAR(255),
                    created_timestamp BIGINT,
                    service_account_client_link VARCHAR(36))""");
        jdbc.execute("""
                CREATE TABLE keycloak.keycloak_role (
                    id VARCHAR(255) PRIMARY KEY,
                    name VARCHAR(255),
                    realm_id VARCHAR(255))""");
        jdbc.execute("""
                CREATE TABLE keycloak.user_role_mapping (
                    user_id VARCHAR(255),
                    role_id VARCHAR(255))""");

        jdbc.update("INSERT INTO keycloak.realm (id, name) VALUES (?, 'jordylab'), (?, 'master')",
                JORDYLAB_REALM, MASTER_REALM);
        jdbc.update("""
                        INSERT INTO keycloak.keycloak_role (id, name, realm_id)
                        VALUES (?, 'admin', ?), (?, 'guest', ?), (?, 'app-gamecatalog', ?), (?, 'admin', ?)""",
                ADMIN_ROLE, JORDYLAB_REALM,
                GUEST_ROLE, JORDYLAB_REALM,
                APP_GAME_CATALOG_ROLE, JORDYLAB_REALM,
                MASTER_ADMIN_ROLE, MASTER_REALM);

        insertUser("aaaaaaaa-1111-4111-8111-111111111111", "Guest.Person", "Ada", "Lovelace", JORDYLAB_REALM, null);
        insertUser("bbbbbbbb-2222-4222-8222-222222222222", "jordy", "Jordy", "Swinnen", JORDYLAB_REALM, null);
        insertUser("cccccccc-3333-4333-8333-333333333333", "plain", null, null, JORDYLAB_REALM, null);
        insertUser("dddddddd-4444-4444-8444-444444444444", "service-account-scan", null, null, JORDYLAB_REALM,
                "99999999-9999-4999-8999-999999999999");
        insertUser("eeeeeeee-5555-4555-8555-555555555555", "master-admin", null, null, MASTER_REALM, null);

        mapRole("aaaaaaaa-1111-4111-8111-111111111111", GUEST_ROLE);
        mapRole("aaaaaaaa-1111-4111-8111-111111111111", APP_GAME_CATALOG_ROLE);
        mapRole("bbbbbbbb-2222-4222-8222-222222222222", ADMIN_ROLE);
        mapRole("cccccccc-3333-4333-8333-333333333333", GUEST_ROLE);
        mapRole("dddddddd-4444-4444-8444-444444444444", ADMIN_ROLE);
        mapRole("eeeeeeee-5555-4555-8555-555555555555", MASTER_ADMIN_ROLE);
    }

    private void insertUser(String id, String username, String firstName, String lastName, String realm,
            String serviceAccountClientLink) {
        jdbc.update("""
                        INSERT INTO keycloak.user_entity
                            (id, username, first_name, last_name, realm_id, created_timestamp, service_account_client_link)
                        VALUES (?, ?, ?, ?, ?, 1700000000000, ?)""",
                id, username, firstName, lastName, realm, serviceAccountClientLink);
    }

    private void mapRole(String userId, String roleId) {
        jdbc.update("INSERT INTO keycloak.user_role_mapping (user_id, role_id) VALUES (?, ?)",
                userId, roleId);
    }
}
