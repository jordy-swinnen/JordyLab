package dev.jordy.jordylab.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
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
    static GenericContainer<?> keycloak = new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.3.2"))
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
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfiguration {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer("pgvector/pgvector:pg16");
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

    private static String authServerUrl() {
        return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
    }
}
