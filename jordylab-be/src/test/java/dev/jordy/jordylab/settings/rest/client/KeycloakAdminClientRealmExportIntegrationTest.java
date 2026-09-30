package dev.jordy.jordylab.settings.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.settings.KeycloakAdminProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Drives the backend's own {@link KeycloakAdminClient} against the production Keycloak version
 * importing the real dev realm export — the same service-account permissions production has.
 * {@code KeycloakIntegrationTest} uses a separate test realm whose service-account roles are
 * granted in code, which hid BUG-031 (no scope mapping) and BUG-033 (non-existent role) until
 * production.
 */
@Testcontainers
class KeycloakAdminClientRealmExportIntegrationTest {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.7.4";
    private static final String REALM = "jordylab";
    private static final String BOOTSTRAP_ADMIN = "test-admin";
    private static final String BOOTSTRAP_PASSWORD = "test-admin-password";
    private static final String PENDING_USERNAME = "pending.person@example.com";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    static GenericContainer<?> keycloak = new GenericContainer<>(DockerImageName.parse(KEYCLOAK_IMAGE))
            .withExposedPorts(8080)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", BOOTSTRAP_ADMIN)
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", BOOTSTRAP_PASSWORD)
            .withEnv("JAVA_OPTS_APPEND", "-Xms128m -Xmx512m -XX:MaxMetaspaceSize=256m")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("compose/keycloak-realm-export.json")),
                    "/opt/keycloak/data/import/jordylab-realm.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(Wait.forHttp("/realms/" + REALM)
                    .forPort(8080)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    private static final RestClient HTTP = RestClient.create();

    private static KeycloakAdminClient adminClient;

    @BeforeAll
    static void connectAsTheBackendServiceAccount() {
        String bootstrapToken = bootstrapAdminToken();
        String backendSecret = regenerateBackendClientSecret(bootstrapToken);
        createPendingUser(bootstrapToken);

        KeycloakAdminProperties properties = new KeycloakAdminProperties(
                serverUrl(), REALM, "jordylab-backend", backendSecret, 10_000);
        adminClient = new KeycloakAdminClient(properties, OBJECT_MAPPER);
        adminClient.init();
    }

    @Test
    void listsUsersWithTheRealmServiceAccountPermissions() {
        List<String> emails = adminClient.listUsers().stream().map(KeycloakAdminClient.KeycloakUser::email).toList();

        assertThat(emails).contains(PENDING_USERNAME);
    }

    @Test
    void grantsAndRevokesTheGuestRoleWithTheRealmServiceAccountPermissions() {
        KeycloakAdminClient.KeycloakUser pending = pendingUser();

        adminClient.grantRealmRole(pending.id(), "guest");
        KeycloakAdminClient.KeycloakUser approved = adminClient.findUser(pending.id()).orElseThrow();
        adminClient.revokeRealmRole(pending.id(), "guest");
        KeycloakAdminClient.KeycloakUser revoked = adminClient.findUser(pending.id()).orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(approved.realmRoles()).contains("guest");
            softly.assertThat(revoked.realmRoles()).doesNotContain("guest");
        });
    }

    private static KeycloakAdminClient.KeycloakUser pendingUser() {
        return adminClient.listUsers().stream()
                .filter(user -> PENDING_USERNAME.equals(user.email()))
                .findFirst()
                .orElseThrow();
    }

    private static String serverUrl() {
        return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
    }

    private static String bootstrapAdminToken() {
        String form = "grant_type=password&client_id=admin-cli"
                + "&username=" + URLEncoder.encode(BOOTSTRAP_ADMIN, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(BOOTSTRAP_PASSWORD, StandardCharsets.UTF_8);
        JsonNode response = readTree(HTTP.post()
                .uri(serverUrl() + "/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class));

        return response.path("access_token").asText();
    }

    // The dev export gives jordylab-backend no fixed secret (BUG-029), so the test sets its own.
    private static String regenerateBackendClientSecret(String bootstrapToken) {
        JsonNode clients = readTree(HTTP.get()
                .uri(serverUrl() + "/admin/realms/" + REALM + "/clients?clientId=jordylab-backend")
                .header("Authorization", "Bearer " + bootstrapToken)
                .retrieve()
                .body(String.class));
        String clientUuid = clients.get(0).path("id").asText();
        JsonNode secret = readTree(HTTP.post()
                .uri(serverUrl() + "/admin/realms/" + REALM + "/clients/" + clientUuid + "/client-secret")
                .header("Authorization", "Bearer " + bootstrapToken)
                .retrieve()
                .body(String.class));

        return secret.path("value").asText();
    }

    private static void createPendingUser(String bootstrapToken) {
        HTTP.post()
                .uri(serverUrl() + "/admin/realms/" + REALM + "/users")
                .header("Authorization", "Bearer " + bootstrapToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username": "%s", "email": "%s", "firstName": "Pending", "lastName": "Person", "enabled": true}
                        """.formatted(PENDING_USERNAME, PENDING_USERNAME))
                .retrieve()
                .toBodilessEntity();
    }

    private static JsonNode readTree(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Unreadable Keycloak response", exception);
        }
    }
}
