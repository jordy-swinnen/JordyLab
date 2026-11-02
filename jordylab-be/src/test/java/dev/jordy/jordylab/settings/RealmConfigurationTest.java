package dev.jordy.jordylab.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Guards the two real realm files (dev export and prod import). The integration tests use their own
 * test realm, so drift in these files is otherwise invisible until production.
 */
class RealmConfigurationTest {

    private static final String DEV_REALM = "compose/keycloak-realm-export.json";
    private static final String PROD_REALM = "../deploy/keycloak/realm-prod.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The admin holds every JordyLab role (Jordy, spec 011 Q-07) so the owner can also scan without a
     * hand-assigned role (BUG-028) and always has every app (spec 014 FR-005).
     */
    @ParameterizedTest
    @ValueSource(strings = {DEV_REALM, PROD_REALM})
    void adminIsACompositeOfEveryJordyLabRole(String realmFile) throws IOException {
        JsonNode adminRole = realmRole(realmFile, "admin");
        List<String> composedRealmRoles = new ArrayList<>();
        adminRole.path("composites").path("realm").forEach(role -> composedRealmRoles.add(role.asText()));

        assertSoftly(softly -> {
            softly.assertThat(adminRole.path("composite").asBoolean()).isTrue();
            softly.assertThat(composedRealmRoles)
                    .containsExactlyInAnyOrder("guest", "app-gamecatalog", "gamecatalog-scanner");
        });
    }

    /**
     * Spec 014 R1: one realm role per grantable app, assigned per person. It must not be composite, because the
     * panel grants it to a single person rather than through another role.
     */
    @ParameterizedTest
    @ValueSource(strings = {DEV_REALM, PROD_REALM})
    void appGameCatalogRoleIsASimpleRealmRole(String realmFile) throws IOException {
        JsonNode appRole = realmRole(realmFile, "app-gamecatalog");

        assertSoftly(softly -> {
            softly.assertThat(appRole.path("name").asText()).isEqualTo("app-gamecatalog");
            softly.assertThat(appRole.path("composite").asBoolean(false)).isFalse();
        });
    }

    /**
     * Spec 014 R2: the backend validates JWTs statelessly, so a removed role or a deleted account stops working
     * only when the access token expires. That lifetime (60 s) is the bound behind FR-007, SC-001 and SC-002 and
     * must not drift back to the 30 minutes that left a revoked person working for half an hour.
     */
    @ParameterizedTest
    @ValueSource(strings = {DEV_REALM, PROD_REALM})
    void accessTokenLifetimeKeepsAccessChangesWithinOneMinute(String realmFile) throws IOException {
        JsonNode realm = objectMapper.readTree(Path.of(realmFile).toFile());

        assertThat(realm.path("accessTokenLifespan").asInt()).isEqualTo(60);
    }

    /**
     * {@code jordylab-backend} keeps {@code fullScopeAllowed=false}, so its service-account token only
     * carries the realm-management roles that are explicitly scope-mapped. Without the mapping the
     * Admin REST API answers 403 and Settings → Users fails (BUG-031).
     */
    @ParameterizedTest
    @ValueSource(strings = {DEV_REALM, PROD_REALM})
    void backendServiceAccountTokenCarriesTheUserAdministrationRoles(String realmFile) throws IOException {
        JsonNode realm = objectMapper.readTree(Path.of(realmFile).toFile());
        List<String> mappedRoles = new ArrayList<>();
        for (JsonNode mapping : realm.path("clientScopeMappings").path("realm-management")) {
            if ("jordylab-backend".equals(mapping.path("client").asText())) {
                mapping.path("roles").forEach(role -> mappedRoles.add(role.asText()));
            }
        }
        List<String> serviceAccountClientRoles = new ArrayList<>();
        for (JsonNode user : realm.path("users")) {
            if ("service-account-jordylab-backend".equals(user.path("username").asText())) {
                user.path("clientRoles").path("realm-management")
                        .forEach(role -> serviceAccountClientRoles.add(role.asText()));
            }
        }

        assertSoftly(softly -> {
            softly.assertThat(backendClient(realm).path("fullScopeAllowed").asBoolean(true)).isFalse();
            softly.assertThat(mappedRoles)
                    .containsExactlyInAnyOrder("view-users", "manage-users", "view-realm", "view-clients");
            // The import applies the service-account user's clientRoles; the scope mapping alone did not grant
            // view-clients on a fresh realm (verified 2026-11-02), so both must list it.
            softly.assertThat(serviceAccountClientRoles)
                    .containsExactlyInAnyOrder("view-users", "manage-users", "view-realm", "view-clients");
        });
    }

    /**
     * The native app's identity entries were removed with it (spec 017 US4): no realm file may
     * reintroduce the App Link client, the release-publishing client or its CI-only role.
     */
    @ParameterizedTest
    @ValueSource(strings = {DEV_REALM, PROD_REALM})
    void nativeAppClientsAndRoleAreGone(String realmFile) throws IOException {
        JsonNode realm = objectMapper.readTree(Path.of(realmFile).toFile());
        List<String> clientIds = new ArrayList<>();
        realm.path("clients").forEach(client -> clientIds.add(client.path("clientId").asText()));
        List<String> roleNames = new ArrayList<>();
        realm.path("roles").path("realm").forEach(role -> roleNames.add(role.path("name").asText()));

        assertSoftly(softly -> {
            softly.assertThat(clientIds).doesNotContain("jordylab-mobile", "mobile-release-ci");
            softly.assertThat(roleNames).doesNotContain("mobile-release-publisher");
        });
    }

    /**
     * The dev realm's backend secret and the local profile must match, or Settings → Users fails
     * locally with a 401 from the token endpoint (BUG-029).
     */
    @Test
    void localProfileUsesTheDevRealmBackendSecret() throws IOException {
        JsonNode devBackend = backendClient(objectMapper.readTree(Path.of(DEV_REALM).toFile()));
        String localSecret = new YamlPropertySourceLoader()
                .load("application-local.yaml", new FileSystemResource("src/main/resources/application-local.yaml"))
                .getFirst()
                .getProperty("jordylab.settings.keycloak.admin-client-secret")
                .toString();

        assertThat(devBackend.path("secret").asText()).isEqualTo(localSecret);
    }

    /** The committed dev-only backend secret must never reach the production realm (review on #43). */
    @Test
    void prodRealmTakesTheBackendSecretFromTheEnvironment() throws IOException {
        JsonNode prodBackend = backendClient(objectMapper.readTree(Path.of(PROD_REALM).toFile()));

        assertThat(prodBackend.path("secret").asText()).isEqualTo("${KEYCLOAK_BACKEND_CLIENT_SECRET}");
    }

    private JsonNode backendClient(JsonNode realm) {
        return client(realm, "jordylab-backend");
    }

    private JsonNode client(JsonNode realm, String clientId) {
        for (JsonNode client : realm.path("clients")) {
            if (clientId.equals(client.path("clientId").asText())) {
                return client;
            }
        }
        throw new IllegalStateException("Client " + clientId + " missing");
    }

    private JsonNode realmRole(String realmFile, String roleName) throws IOException {
        JsonNode realm = objectMapper.readTree(Path.of(realmFile).toFile());
        for (JsonNode role : realm.path("roles").path("realm")) {
            if (roleName.equals(role.path("name").asText())) {
                return role;
            }
        }
        throw new IllegalStateException("Role " + roleName + " missing from " + realmFile);
    }
}
