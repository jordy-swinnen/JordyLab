package dev.jordy.jordylab.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Production reaches Keycloak two ways: the backend's Admin REST calls must stay inside the
 * cluster (the public Gateway deliberately does not route {@code /auth/admin}, spec 008 FR-009),
 * while the downloadable scanner logs in from the user's own machine and needs the public URL.
 */
class ProdKeycloakUrlConfigurationTest {

    private static final String PUBLIC_KEYCLOAK_URL = "https://jordylab.be/auth";
    private static final String INTERNAL_KEYCLOAK_URL = "http://keycloak:8080/auth";
    private static final String MAIN_RESOURCES = "src/main/resources/";

    @Test
    void prodProfileSendsAdminCallsInsideTheClusterAndGivesTheScannerThePublicUrl() throws IOException {
        StandardEnvironment environment = prodEnvironment();

        assertSoftly(softly -> {
            softly.assertThat(environment.getProperty("jordylab.settings.keycloak.server-url"))
                    .isEqualTo(INTERNAL_KEYCLOAK_URL);
            softly.assertThat(environment.getProperty("jordylab.script.keycloak-url"))
                    .isEqualTo(PUBLIC_KEYCLOAK_URL);
        });
    }

    private static StandardEnvironment prodEnvironment() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addFirst(new MapPropertySource("prod-configmap", Map.of(
                "KEYCLOAK_URL", PUBLIC_KEYCLOAK_URL,
                "KEYCLOAK_INTERNAL_URL", INTERNAL_KEYCLOAK_URL)));
        // Profile-specific file wins over the shared file, as in Spring Boot's own ordering.
        load("application-prod.yaml").forEach(propertySources::addLast);
        load("application.yaml").forEach(propertySources::addLast);
        return environment;
    }

    // Read from src/main/resources on purpose: src/test/resources has its own application.yaml
    // that would shadow the real one on the test classpath.
    private static List<PropertySource<?>> load(String fileName) throws IOException {
        return new YamlPropertySourceLoader().load(fileName, new FileSystemResource(MAIN_RESOURCES + fileName));
    }
}
