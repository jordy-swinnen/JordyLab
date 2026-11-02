package dev.jordy.jordylab.settings.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.jordy.jordylab.settings.KeycloakAdminProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@WireMockTest(httpPort = 9995)
class KeycloakAdminClientTest {

    private static final String BASE = "http://localhost:9995";

    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        KeycloakAdminProperties properties = new KeycloakAdminProperties(BASE, "jordylab-test",
                "jordylab-backend", "backend-secret", 2000);
        client = new KeycloakAdminClient(properties, new ObjectMapper());
        client.init();
    }

    @Test
    void listsUsersWithTheirRealmRoles() {
        stubToken();
        stubFor(get(urlEqualTo("/admin/realms/jordylab-test/users?max=1000")).willReturn(json("""
                [ {"id": "u1", "email": "a@b.com", "firstName": "Ada", "lastName": "Palmer",
                   "createdTimestamp": 1700000000000, "enabled": true} ]
                """)));
        stubFor(get(urlPathEqualTo("/admin/realms/jordylab-test/users/u1/role-mappings/realm")).willReturn(json("""
                [ {"id": "r1", "name": "guest"} ]
                """)));

        List<KeycloakAdminClient.KeycloakUser> users = client.listUsers();

        assertThat(users).hasSize(1);
        KeycloakAdminClient.KeycloakUser user = users.get(0);
        assertSoftly(softly -> {
            softly.assertThat(user.id()).isEqualTo("u1");
            softly.assertThat(user.email()).isEqualTo("a@b.com");
            softly.assertThat(user.enabled()).isTrue();
            softly.assertThat(user.realmRoles()).containsExactly("guest");
        });
    }

    @Test
    void findUserReturnsEmptyOn404() {
        stubToken();
        stubFor(get(urlPathEqualTo("/admin/realms/jordylab-test/users/missing")).willReturn(aResponse().withStatus(404)));

        Optional<KeycloakAdminClient.KeycloakUser> user = client.findUser("missing");

        assertThat(user).isEmpty();
    }

    @Test
    void grantsARealmRoleByLookingUpItsRepresentationFirst() {
        stubToken();
        stubFor(get(urlPathEqualTo("/admin/realms/jordylab-test/roles/guest")).willReturn(json("""
                {"id": "role-guest-id", "name": "guest"}
                """)));
        stubFor(post(urlPathEqualTo("/admin/realms/jordylab-test/users/u1/role-mappings/realm"))
                .willReturn(aResponse().withStatus(204)));

        client.grantRealmRole("u1", "guest");

        verify(postRequestedFor(urlPathEqualTo("/admin/realms/jordylab-test/users/u1/role-mappings/realm"))
                .withRequestBody(equalToJson("""
                        [ {"id": "role-guest-id", "name": "guest"} ]
                        """)));
    }

    @Test
    void revokesARealmRoleWithADeleteCarryingTheRoleBody() {
        stubToken();
        stubFor(get(urlPathEqualTo("/admin/realms/jordylab-test/roles/guest")).willReturn(json("""
                {"id": "role-guest-id", "name": "guest"}
                """)));
        stubFor(delete(urlPathEqualTo("/admin/realms/jordylab-test/users/u1/role-mappings/realm"))
                .willReturn(aResponse().withStatus(204)));

        client.revokeRealmRole("u1", "guest");

        verify(deleteRequestedFor(urlPathEqualTo("/admin/realms/jordylab-test/users/u1/role-mappings/realm")));
    }

    @Test
    void setEnabledReadsThenWritesBackTheFullUserRepresentation() {
        stubToken();
        stubFor(get(urlPathEqualTo("/admin/realms/jordylab-test/users/u1")).willReturn(json("""
                {"id": "u1", "email": "a@b.com", "enabled": true}
                """)));
        stubFor(put(urlPathEqualTo("/admin/realms/jordylab-test/users/u1")).willReturn(aResponse().withStatus(204)));

        client.setEnabled("u1", false);

        verify(putRequestedFor(urlPathEqualTo("/admin/realms/jordylab-test/users/u1"))
                .withRequestBody(equalToJson("""
                        {"id": "u1", "email": "a@b.com", "enabled": false}
                        """)));
    }

    @Test
    void resetsPasswordAsATemporaryPassword() {
        stubToken();
        stubFor(put(urlPathEqualTo("/admin/realms/jordylab-test/users/u1/reset-password"))
                .willReturn(aResponse().withStatus(204)));

        client.resetPassword("u1", "temp-pass-123");

        verify(putRequestedFor(urlPathEqualTo("/admin/realms/jordylab-test/users/u1/reset-password"))
                .withRequestBody(equalToJson("""
                        {"type": "password", "value": "temp-pass-123", "temporary": true}
                        """)));
    }

    @Test
    void endsSessionsWithALogoutCall() {
        stubToken();
        stubFor(post(urlPathEqualTo("/admin/realms/jordylab-test/users/u1/logout"))
                .willReturn(aResponse().withStatus(204)));

        client.endSessions("u1");

        verify(postRequestedFor(urlPathEqualTo("/admin/realms/jordylab-test/users/u1/logout")));
    }

    @Test
    void aTokenRejectedMidFlightIsRefreshedOnceAndTheCallRetried() {
        stubToken();
        stubFor(get(urlPathEqualTo("/admin/realms/jordylab-test/users/u1"))
                .willReturn(aResponse().withStatus(401))
                .willReturn(json("""
                        {"id": "u1", "email": "a@b.com", "enabled": true}
                        """)));
        stubFor(get(urlPathEqualTo("/admin/realms/jordylab-test/users/u1/role-mappings/realm")).willReturn(json("[]")));

        Optional<KeycloakAdminClient.KeycloakUser> user = client.findUser("u1");

        assertThat(user).isPresent();
    }

    @Test
    void aServerFailureRaisesAnExplicitUnavailableException() {
        stubToken();
        stubFor(get(urlEqualTo("/admin/realms/jordylab-test/users?max=1000"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.listUsers()).isInstanceOf(KeycloakUnavailableException.class);
    }

    @Test
    void aFailedTokenRequestRaisesAnExplicitUnavailableException() {
        stubFor(post(urlPathEqualTo("/realms/jordylab-test/protocol/openid-connect/token"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.listUsers()).isInstanceOf(KeycloakUnavailableException.class);
    }

    @Test
    void listsRegularAndOfflineSessionsWithTheirClients() {
        stubToken();
        stubFor(get(urlEqualTo("/admin/realms/jordylab-test/users/u1/sessions")).willReturn(json("""
                [ {"id": "s1", "start": 1000, "lastAccess": 2000, "clients": {"jordylab-host": ""}} ]
                """)));
        stubFor(get(urlEqualTo("/admin/realms/jordylab-test/users/u1/offline-sessions/c1"))
                .willReturn(json("""
                        [ {"id": "s2", "start": 3000, "lastAccess": 4000, "clients": {"jordylab-mobile": ""}} ]
                        """)));

        assertSoftly(softly -> {
            softly.assertThat(client.listUserSessions("u1")).singleElement().satisfies(session -> {
                softly.assertThat(session.id()).isEqualTo("s1");
                softly.assertThat(session.clientIds()).containsExactly("jordylab-host");
            });
            softly.assertThat(client.listOfflineSessions("u1", "c1")).singleElement().satisfies(session ->
                    softly.assertThat(session.clientIds()).containsExactly("jordylab-mobile"));
        });
    }

    @Test
    void findsTheClientUuidOnceAndEndsAnOfflineSessionOnItsOwn() {
        stubToken();
        stubFor(get(urlEqualTo("/admin/realms/jordylab-test/clients?clientId=jordylab-mobile"))
                .willReturn(json("""
                        [ {"id": "c1", "clientId": "jordylab-mobile"} ]
                        """)));
        stubFor(delete(urlEqualTo("/admin/realms/jordylab-test/sessions/s1?isOffline=true"))
                .willReturn(aResponse().withStatus(204)));

        assertThat(client.findClientUuid("jordylab-mobile")).contains("c1");
        assertThat(client.findClientUuid("jordylab-mobile")).contains("c1");
        client.deleteSession("s1", true);

        verify(1, getRequestedFor(urlEqualTo("/admin/realms/jordylab-test/clients?clientId=jordylab-mobile")));
        verify(deleteRequestedFor(urlEqualTo("/admin/realms/jordylab-test/sessions/s1?isOffline=true")));
    }

    @Test
    void deletesAUserAndTreatsAnAlreadyDeletedOneAsDone() {
        stubToken();
        stubFor(delete(urlEqualTo("/admin/realms/jordylab-test/users/u1")).willReturn(aResponse().withStatus(204)));
        stubFor(delete(urlEqualTo("/admin/realms/jordylab-test/users/u2")).willReturn(aResponse().withStatus(404)));

        client.deleteUser("u1");
        client.deleteUser("u2");

        verify(deleteRequestedFor(urlEqualTo("/admin/realms/jordylab-test/users/u1")));
    }

    private void stubToken() {
        stubFor(post(urlPathEqualTo("/realms/jordylab-test/protocol/openid-connect/token"))
                .willReturn(json("""
                        {"access_token": "test-token", "expires_in": 3600, "token_type": "bearer"}
                        """)));
    }

    private ResponseDefinitionBuilder json(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
