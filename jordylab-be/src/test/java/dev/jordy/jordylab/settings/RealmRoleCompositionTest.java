package dev.jordy.jordylab.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * The admin holds every JordyLab role (Jordy, spec 011 Q-07) so the owner can also scan without a
 * hand-assigned role (BUG-028). {@code mobile-release-publisher} is deliberately excluded: only the
 * CI identity may publish Android releases (spec 007 FR-004).
 */
class RealmRoleCompositionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"compose/keycloak-realm-export.json", "../deploy/keycloak/realm-prod.json"})
    void adminIsACompositeOfEveryJordyLabRoleExceptTheCiPublisher(String realmFile) throws IOException {
        JsonNode adminRole = realmRole(realmFile, "admin");
        List<String> composedRealmRoles = new ArrayList<>();
        adminRole.path("composites").path("realm").forEach(role -> composedRealmRoles.add(role.asText()));

        assertSoftly(softly -> {
            softly.assertThat(adminRole.path("composite").asBoolean()).isTrue();
            softly.assertThat(composedRealmRoles).containsExactlyInAnyOrder("guest", "gamecatalog-scanner");
            softly.assertThat(composedRealmRoles).doesNotContain("mobile-release-publisher");
        });
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
