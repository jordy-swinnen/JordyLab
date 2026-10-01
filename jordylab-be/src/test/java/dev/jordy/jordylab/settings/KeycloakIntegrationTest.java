package dev.jordy.jordylab.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Base for tests that need the real identity provider: one Keycloak container per test JVM
 * (realm from {@code keycloak/jordylab-test-realm.json} — admin, guest, pending and scanner
 * users plus the confidential {@code jordylab-backend} client) and one pgvector container.
 * Tokens are obtained through the password grant on the test-only {@code test-public}
 * client, so authorization runs against real Keycloak-issued JWTs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "spring.profiles.active=local")
@Import(KeycloakIntegrationTest.PostgresConfiguration.class)
abstract class KeycloakIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    static GenericContainer<?> keycloak = new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.7.4"))
            .withExposedPorts(8080)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "test-admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "test-admin-password")
            .withEnv("JAVA_OPTS_APPEND", "-Xms128m -Xmx512m -XX:MaxMetaspaceSize=256m")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("keycloak/jordylab-test-realm.json"),
                    "/opt/keycloak/data/import/jordylab-test-realm.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(Wait.forHttp("/realms/jordylab-test")
                    .forPort(8080)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> authServerUrl() + "/realms/jordylab-test");
        registry.add("jordylab.settings.keycloak.server-url", KeycloakIntegrationTest::authServerUrl);
        registry.add("jordylab.settings.keycloak.realm", () -> "jordylab-test");
        registry.add("jordylab.settings.keycloak.admin-client-secret", () -> "test-backend-secret");
        grantServiceAccountAdminRoles();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfiguration {

        private static final PostgreSQLContainer POSTGRES =
                new PostgreSQLContainer("pgvector/pgvector:pg16");

        @Bean(destroyMethod = "")
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return POSTGRES;
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    protected String accessTokenFor(String username, String password) {
        String form = "grant_type=password"
                + "&client_id=test-public"
                + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);

        String response = RestClient.create()
                .post()
                .uri(authServerUrl() + "/realms/jordylab-test/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        try {
            return OBJECT_MAPPER.readTree(response).get("access_token").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Keycloak token response did not contain an access token", exception);
        }
    }

    private static void grantServiceAccountAdminRoles() {
        String adminToken = adminAccessToken();
        String serviceAccountId = findUserId(adminToken, "service-account-jordylab-backend");
        String realmManagementClientId = findClientId(adminToken, "realm-management");

        List<Map<String, String>> roles = List.of(
                realmManagementRole(adminToken, realmManagementClientId, "manage-users"),
                realmManagementRole(adminToken, realmManagementClientId, "view-users"));

        RestClient.create()
                .post()
                .uri(adminApiUrl() + "/users/" + serviceAccountId + "/role-mappings/clients/" + realmManagementClientId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(roles)
                .retrieve()
                .toBodilessEntity();
    }

    private static String adminAccessToken() {
        String form = "grant_type=password"
                + "&client_id=admin-cli"
                + "&username=" + URLEncoder.encode("test-admin", StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode("test-admin-password", StandardCharsets.UTF_8);

        String response = RestClient.create()
                .post()
                .uri(authServerUrl() + "/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        try {
            return OBJECT_MAPPER.readTree(response).get("access_token").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Keycloak admin token response did not contain an access token", exception);
        }
    }

    private static String findUserId(String adminToken, String username) {
        String response = RestClient.create()
                .get()
                .uri(adminApiUrl() + "/users?exact=true&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .body(String.class);

        try {
            JsonNode users = OBJECT_MAPPER.readTree(response);
            if (!users.isArray() || users.isEmpty()) {
                throw new IllegalStateException("Keycloak user not found: " + username);
            }
            return users.get(0).get("id").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not resolve Keycloak user id for " + username, exception);
        }
    }

    private static String findClientId(String adminToken, String clientId) {
        String response = RestClient.create()
                .get()
                .uri(adminApiUrl() + "/clients?clientId=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .body(String.class);

        try {
            JsonNode clients = OBJECT_MAPPER.readTree(response);
            if (!clients.isArray() || clients.isEmpty()) {
                throw new IllegalStateException("Keycloak client not found: " + clientId);
            }
            return clients.get(0).get("id").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not resolve Keycloak client id for " + clientId, exception);
        }
    }

    private static Map<String, String> realmManagementRole(String adminToken, String realmManagementClientId, String roleName) {
        String response = RestClient.create()
                .get()
                .uri(adminApiUrl() + "/clients/" + realmManagementClientId + "/roles/" + roleName)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .body(String.class);

        try {
            JsonNode role = OBJECT_MAPPER.readTree(response);
            return Map.of(
                    "id", role.get("id").asText(),
                    "name", role.get("name").asText());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not resolve realm-management role " + roleName, exception);
        }
    }

    private static String adminApiUrl() {
        return authServerUrl() + "/admin/realms/jordylab-test";
    }

    private static String authServerUrl() {
        return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
    }
}
