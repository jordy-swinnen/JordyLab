package dev.jordy.jordylab.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogAccess;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Spec 014 stories that need a real identity provider: a grant reaches an approved person, a removal takes the app away
 * without signing them out, and FNA can never be granted. Runs against the Keycloak test realm, whose access-token
 * lifetime is 60 s (RealmConfigurationTest guards the real realm files).
 */
class UserManagementIntegrationTest extends KeycloakIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NO_APPS_USERNAME = "guest-no-apps";
    private static final String NO_APPS_PASSWORD = "guest-no-apps-password";

    @Test
    void grantingTheGameCatalogLetsTheGuestInAndRemovingItTakesItAwayWithoutSigningThemOut() throws Exception {
        String admin = accessTokenFor("admin-user", "admin-password");
        String guest = accessTokenFor(NO_APPS_USERNAME, NO_APPS_PASSWORD);
        UUID guestId = userIdOf(admin, NO_APPS_USERNAME);

        assertSoftly(softly -> softly.assertThat(status(as(get("/api/gamecatalog/games"), guest)))
                .as("no grant yet").isEqualTo(403));

        assertSoftly(softly -> softly.assertThat(status(as(put("/api/settings/users/{id}/apps/{app}",
                        guestId, GameCatalogAccess.APP_KEY), admin)))
                .as("grant").isEqualTo(204));

        String guestWithGrant = accessTokenFor(NO_APPS_USERNAME, NO_APPS_PASSWORD);
        assertSoftly(softly -> {
            softly.assertThat(status(as(get("/api/gamecatalog/games"), guestWithGrant))).isEqualTo(200);
            softly.assertThat(status(as(get("/api/fna/articles"), guestWithGrant))).isEqualTo(403);
        });

        assertSoftly(softly -> softly.assertThat(status(as(delete("/api/settings/users/{id}/apps/{app}",
                        guestId, GameCatalogAccess.APP_KEY), admin)))
                .as("remove").isEqualTo(204));

        String guestAfterRemoval = accessTokenFor(NO_APPS_USERNAME, NO_APPS_PASSWORD);
        assertSoftly(softly -> {
            softly.assertThat(status(as(get("/api/gamecatalog/games"), guestAfterRemoval))).isEqualTo(403);
            // Not signed out: approval is untouched, so a non-app route still authorizes the person.
            softly.assertThat(status(as(get("/api/mobile/releases/latest"), guestAfterRemoval))).isEqualTo(404);
        });
    }

    @Test
    void fnaCanNeverBeGrantedAndTheAdminCannotChangeTheirOwnAccount() throws Exception {
        String admin = accessTokenFor("admin-user", "admin-password");
        UUID guestId = userIdOf(admin, NO_APPS_USERNAME);
        UUID adminId = userIdOf(admin, "admin-user");

        assertSoftly(softly -> {
            softly.assertThat(status(as(put("/api/settings/users/{id}/apps/fna", guestId), admin)))
                    .as("FNA is refused by the server").isEqualTo(400);
            softly.assertThat(status(as(put("/api/settings/users/{id}/apps/{app}", adminId,
                    GameCatalogAccess.APP_KEY), admin)))
                    .as("the admin cannot grant to themselves").isEqualTo(400);
            softly.assertThat(status(as(get("/api/gamecatalog/games"), admin)))
                    .as("the admin still reaches everything").isEqualTo(200);
        });
    }

    /** Spec 014 US4: the account, its sessions and its local data go, and the person cannot sign in again. */
    @Test
    void deletingAPersonEndsTheirSessionsAndRemovesTheAccount() throws Exception {
        String admin = accessTokenFor("admin-user", "admin-password");
        UUID userId = userIdOf(admin, "delete-me");
        accessTokenFor("delete-me", "delete-me-password");
        accessTokenFor("delete-me", "delete-me-password");

        MvcResult sessions = mockMvc.perform(get("/api/settings/users/{id}/sessions", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andReturn();
        int sessionCount = OBJECT_MAPPER.readTree(sessions.getResponse().getContentAsString())
                .path("sessions").size();

        mockMvc.perform(delete("/api/settings/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andReturn();

        assertSoftly(softly -> {
            softly.assertThat(sessionCount).as("two sign-ins are listed").isGreaterThanOrEqualTo(2);
            softly.assertThatThrownBy(() -> accessTokenFor("delete-me", "delete-me-password"))
                    .as("the account can no longer sign in")
                    .isInstanceOf(RuntimeException.class);
        });
    }

    /** Spec 014 US5: ending one session signs out that device only; the other stays listed. */
    @Test
    void endingOneSessionLeavesTheOther() throws Exception {
        String admin = accessTokenFor("admin-user", "admin-password");
        UUID userId = userIdOf(admin, NO_APPS_USERNAME);
        accessTokenFor(NO_APPS_USERNAME, NO_APPS_PASSWORD);
        accessTokenFor(NO_APPS_USERNAME, NO_APPS_PASSWORD);

        MvcResult listed = mockMvc.perform(get("/api/settings/users/{id}/sessions", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andReturn();
        JsonNode before = OBJECT_MAPPER.readTree(listed.getResponse().getContentAsString()).path("sessions");
        String firstSessionId = before.get(0).path("id").asText();
        String firstKind = before.get(0).path("kind").asText();

        mockMvc.perform(delete("/api/settings/users/{id}/sessions/{sessionId}", userId, firstSessionId)
                        .param("kind", firstKind)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andReturn();

        MvcResult after = mockMvc.perform(get("/api/settings/users/{id}/sessions", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andReturn();
        JsonNode remaining = OBJECT_MAPPER.readTree(after.getResponse().getContentAsString()).path("sessions");

        assertSoftly(softly -> {
            softly.assertThat(before.size()).isGreaterThanOrEqualTo(2);
            softly.assertThat(remaining.size()).isEqualTo(before.size() - 1);
        });
    }

    private UUID userIdOf(String adminToken, String username) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/settings/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andReturn();
        JsonNode users = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).path("users");
        String email = username + "@jordylab.test";
        for (JsonNode user : users) {
            if (email.equals(user.path("email").asText())) {
                return UUID.fromString(user.path("id").asText());
            }
        }

        throw new IllegalStateException("User not listed: " + username);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private int status(MockHttpServletRequestBuilder request) {
        try {
            return mockMvc.perform(request).andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new IllegalStateException("MockMvc request failed", exception);
        }
    }
}
