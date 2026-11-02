package dev.jordy.jordylab.settings.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.settings.KeycloakIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Spec 014 gate checks, run against the real Keycloak 26.7.4 test container before the panel is built on top of them
 * (research R3, quickstart section 1).
 *
 * <ul>
 *   <li><strong>T-V1</strong>: one phone offline session can be deleted on its own with {@code ?isOffline=true}, and the
 *   refresh token of that same offline grant then stops working. Phone sessions are offline sessions of the
 *   {@code jordylab-mobile} client, which the regular logout does not touch.</li>
 *   <li><strong>T-V2</strong>: the {@code jordylab-backend} service account may end sessions and delete users — the
 *   two Admin REST calls the deletion and session stories rely on. Its scopes ({@code manage-users}, {@code view-users})
 *   are granted by the base class, mirroring the live realm (deploy README, 2026-09-30).</li>
 * </ul>
 *
 * <p>This test uses the Admin REST API directly, not the production client, so it states the Keycloak behaviour
 * independently of our code. The production client is covered by its own unit test.
 */
class KeycloakSessionVerificationIntegrationTest extends KeycloakIntegrationTest {

    private static final String REALM = "jordylab-test";
    private static final String MOBILE_CLIENT_ID = "jordylab-mobile";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void onePhoneOfflineSessionCanBeDeletedOnItsOwn() throws Exception {
        String adminToken = masterAdminToken();
        String offlineUserId = createUser(adminToken, "offline-user", "offline-password", "Offline", "User");

        JsonNode offlineGrant = postForm(tokenUrl(), passwordGrant(MOBILE_CLIENT_ID, "offline-user", "offline-password",
                "openid offline_access"));
        String offlineRefreshToken = offlineGrant.path("refresh_token").asText();
        String mobileClientUuid = findClientUuid(adminToken, MOBILE_CLIENT_ID);
        JsonNode offlineSessions = OBJECT_MAPPER.readTree(get(adminToken,
                adminApiUrl() + "/users/" + offlineUserId + "/offline-sessions/" + mobileClientUuid));
        String sessionId = offlineSessions.get(0).path("id").asText();

        Boolean deleted = delete(adminToken, sessionsApiUrl() + "/" + sessionId + "?isOffline=true");
        JsonNode afterDelete = OBJECT_MAPPER.readTree(get(adminToken,
                adminApiUrl() + "/users/" + offlineUserId + "/offline-sessions/" + mobileClientUuid));
        boolean sameGrantStillRefreshes = refreshSucceeds(MOBILE_CLIENT_ID, offlineRefreshToken);

        assertSoftly(softly -> {
            softly.assertThat(offlineRefreshToken).as("offline grant returned a refresh token").isNotBlank();
            softly.assertThat(offlineSessions).as("the jordylab-mobile grant created exactly one offline session").hasSize(1);
            softly.assertThat(deleted).isTrue();
            softly.assertThat(afterDelete).as("the deleted offline session is gone").isEmpty();
            softly.assertThat(sameGrantStillRefreshes).as("its refresh token no longer works").isFalse();
        });
    }

    @Test
    void backendServiceAccountCanEndSessionsAndDeleteUsers() throws Exception {
        String adminToken = masterAdminToken();
        String backendToken = clientCredentialsToken("jordylab-backend", "test-backend-secret");
        String throwawayUserId = createUser(adminToken, "throwaway-user", "throwaway-password", "Throwaway", "User");
        String throwawayToken = postForm(tokenUrl(), passwordGrant("test-public", "throwaway-user", "throwaway-password",
                "openid")).path("access_token").asText();

        Boolean loggedOut = post(backendToken, adminApiUrl() + "/users/" + throwawayUserId + "/logout");
        boolean sessionEnded = OBJECT_MAPPER.readTree(get(adminToken,
                adminApiUrl() + "/users/" + throwawayUserId + "/sessions")).isEmpty();
        Boolean deleted = delete(backendToken, adminApiUrl() + "/users/" + throwawayUserId);

        assertSoftly(softly -> {
            softly.assertThat(rolesOf(backendToken)).as("backend service-account roles").contains("manage-users");
            softly.assertThat(throwawayToken).isNotBlank();
            softly.assertThat(loggedOut).isTrue();
            softly.assertThat(sessionEnded).isTrue();
            softly.assertThat(deleted).isTrue();
        });
    }

    private static String createUser(String adminToken, String username, String password, String firstName,
            String lastName) throws Exception {
        String body = """
                {
                  "username": "%s",
                  "enabled": true,
                  "email": "%s@jordylab.test",
                  "emailVerified": true,
                  "firstName": "%s",
                  "lastName": "%s",
                  "credentials": [{ "type": "password", "value": "%s", "temporary": false }]
                }
                """.formatted(username, username, firstName, lastName, password);
        RestClient.create()
                .post()
                .uri(adminApiUrl() + "/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        return findUserId(adminToken, username);
    }

    private static String passwordGrant(String clientId, String username, String password, String scope) {
        return "grant_type=password"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8)
                + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
    }

    private static boolean refreshSucceeds(String clientId, String refreshToken) {
        String form = "grant_type=refresh_token"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);
        try {
            postForm(tokenUrl(), form);

            return true;
        } catch (HttpClientErrorException refreshRefused) {
            return false;
        }
    }

    private static String clientCredentialsToken(String clientId, String clientSecret) {
        String form = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

        return postForm(tokenUrl(), form).path("access_token").asText();
    }

    private static JsonNode postForm(String url, String form) {
        String response = RestClient.create()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        try {
            return OBJECT_MAPPER.readTree(response);
        } catch (Exception exception) {
            throw new IllegalStateException("Token response unreadable", exception);
        }
    }

    private static List<String> rolesOf(String accessToken) {
        try {
            JsonNode payload = OBJECT_MAPPER.readTree(
                    java.util.Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
            List<String> roles = new java.util.ArrayList<>();
            payload.path("resource_access").path("realm-management").path("roles")
                    .forEach(role -> roles.add(role.asText()));

            return roles;
        } catch (Exception exception) {
            return List.of("unreadable token");
        }
    }

    private static String masterAdminToken() {
        String form = "grant_type=password"
                + "&client_id=admin-cli"
                + "&username=" + URLEncoder.encode("test-admin", StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode("test-admin-password", StandardCharsets.UTF_8);

        return postForm(authServerUrl() + "/realms/master/protocol/openid-connect/token", form)
                .path("access_token").asText();
    }

    private static String findUserId(String adminToken, String username) throws Exception {
        JsonNode users = OBJECT_MAPPER.readTree(get(adminToken,
                adminApiUrl() + "/users?exact=true&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)));
        if (users.isEmpty()) {
            throw new IllegalStateException("Keycloak user not found: " + username);
        }

        return users.get(0).path("id").asText();
    }

    private static String findClientUuid(String adminToken, String clientId) throws Exception {
        JsonNode clients = OBJECT_MAPPER.readTree(get(adminToken,
                adminApiUrl() + "/clients?clientId=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)));
        if (clients.isEmpty()) {
            throw new IllegalStateException("Keycloak client not found: " + clientId);
        }

        return clients.get(0).path("id").asText();
    }

    private static String get(String token, String url) {
        return RestClient.create()
                .get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(String.class);
    }

    private static Boolean post(String token, String url) {
        try {
            return RestClient.create()
                    .post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (HttpClientErrorException refused) {
            return false;
        }
    }

    private static Boolean delete(String token, String url) {
        return RestClient.create()
                .delete()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .is2xxSuccessful();
    }

    private static String tokenUrl() {
        return authServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/token";
    }

    private static String adminApiUrl() {
        return authServerUrl() + "/admin/realms/" + REALM;
    }

    private static String sessionsApiUrl() {
        return authServerUrl() + "/admin/realms/" + REALM + "/sessions";
    }
}
