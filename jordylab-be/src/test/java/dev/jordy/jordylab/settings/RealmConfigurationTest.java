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
 * Guards the two real realm files (dev export and prod import). The integration tests use their own
 * test realm, so drift in these files is otherwise invisible until production.
 */
class RealmConfigurationTest {

    private static final String DEV_REALM = "compose/keycloak-realm-export.json";
    private static final String PROD_REALM = "../deploy/keycloak/realm-prod.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The admin holds every JordyLab role (Jordy, spec 011 Q-07) so the owner can also scan without a
     * hand-assigned role (BUG-028). {@code mobile-release-publisher} is deliberately excluded: only
     * the CI identity may publish Android releases (spec 007 FR-004).
     */
    @ParameterizedTest
    @ValueSource(strings = {DEV_REALM, PROD_REALM})
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

        assertSoftly(softly -> {
            softly.assertThat(backendClient(realm).path("fullScopeAllowed").asBoolean(true)).isFalse();
            softly.assertThat(mappedRoles).containsExactlyInAnyOrder("view-users", "manage-users", "view-realm");
        });
    }

    /**
     * The native app logs in through the system browser with PKCE and returns via the App Link
     * callback (spec 007 D2/D13); it never gets the password grant.
     */
    @ParameterizedTest
    @ValueSource(strings = {DEV_REALM, PROD_REALM})
    void mobileClientIsAPublicPkceClientReturningToTheMobileCallback(String realmFile) throws IOException {
        JsonNode mobileClient = client(objectMapper.readTree(Path.of(realmFile).toFile()), "jordylab-mobile");
        List<String> redirectUris = new ArrayList<>();
        mobileClient.path("redirectUris").forEach(uri -> redirectUris.add(uri.asText()));

        assertSoftly(softly -> {
            softly.assertThat(mobileClient.path("publicClient").asBoolean()).isTrue();
            softly.assertThat(mobileClient.path("directAccessGrantsEnabled").asBoolean(true)).isFalse();
            softly.assertThat(mobileClient.path("attributes").path("pkce.code.challenge.method").asText()).isEqualTo("S256");
            softly.assertThat(redirectUris).singleElement().asString().endsWith("/mobile/callback");
        });
    }

    /** Only the CI service account may publish Android releases (spec 007 FR-004). */
    @ParameterizedTest
    @ValueSource(strings = {DEV_REALM, PROD_REALM})
    void onlyTheCiServiceAccountHoldsTheReleasePublisherRole(String realmFile) throws IOException {
        JsonNode realm = objectMapper.readTree(Path.of(realmFile).toFile());
        List<String> holders = new ArrayList<>();
        for (JsonNode user : realm.path("users")) {
            user.path("realmRoles").forEach(role -> {
                if ("mobile-release-publisher".equals(role.asText())) {
                    holders.add(user.path("username").asText());
                }
            });
        }
        JsonNode ciClient = client(realm, "mobile-release-ci");

        assertSoftly(softly -> {
            softly.assertThat(holders).containsExactly("service-account-mobile-release-ci");
            softly.assertThat(ciClient.path("serviceAccountsEnabled").asBoolean()).isTrue();
            softly.assertThat(ciClient.path("publicClient").asBoolean(true)).isFalse();
        });
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
