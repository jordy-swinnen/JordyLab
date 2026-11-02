package dev.jordy.jordylab.settings.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.jordy.jordylab.settings.KeycloakAdminProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin typed client over the Keycloak Admin REST API (research D3) — a plain
 * {@link RestClient}, not the {@code keycloak-admin-client} artifact, whose 26.0.x line no
 * longer tracks the 26.3.x server. Authenticates as the confidential {@code jordylab-backend}
 * service account (client-credentials grant, token cached in memory and refreshed on expiry
 * or a 401). Every failure — token or Admin REST — raises {@link KeycloakUnavailableException}
 * so callers fail fast instead of acting on a partial result; the client secret and access
 * token are never logged.
 */
@Component
@RequiredArgsConstructor
public class KeycloakAdminClient {

    private final KeycloakAdminProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<String, String> realmRoleCache = new ConcurrentHashMap<>();
    private final Map<String, String> clientUuidCache = new ConcurrentHashMap<>();

    private RestClient restClient;
    private volatile String accessToken;
    private volatile Instant tokenExpiresAt;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeoutMs());
        requestFactory.setReadTimeout(properties.timeoutMs());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public List<KeycloakUser> listUsers() {
        JsonNode usersNode = readTree(call(HttpMethod.GET, "/users?max=1000", null));
        List<KeycloakUser> users = new ArrayList<>();
        for (JsonNode userNode : usersNode) {
            users.add(toUser(userNode));
        }

        return users;
    }

    public Optional<KeycloakUser> findUser(String userId) {
        try {
            return Optional.of(toUser(readTree(call(HttpMethod.GET, "/users/" + userId, null))));
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        }
    }

    public void grantRealmRole(String userId, String roleName) {
        call(HttpMethod.POST, "/users/" + userId + "/role-mappings/realm", "[" + realmRole(roleName) + "]");
    }

    public void revokeRealmRole(String userId, String roleName) {
        call(HttpMethod.DELETE, "/users/" + userId + "/role-mappings/realm", "[" + realmRole(roleName) + "]");
    }

    public void setEnabled(String userId, boolean enabled) {
        ObjectNode user = (ObjectNode) readTree(call(HttpMethod.GET, "/users/" + userId, null));
        user.put("enabled", enabled);
        call(HttpMethod.PUT, "/users/" + userId, user.toString());
    }

    public void resetPassword(String userId, String temporaryPassword) {
        String body = objectMapper.createObjectNode()
                .put("type", "password")
                .put("value", temporaryPassword)
                .put("temporary", true)
                .toString();
        call(HttpMethod.PUT, "/users/" + userId + "/reset-password", body);
    }

    public void endSessions(String userId) {
        call(HttpMethod.POST, "/users/" + userId + "/logout", null);
    }

    /**
     * Deletes an account (spec 014 FR-015). Used only after the account is disabled and its sessions are ended, so a
     * failure here leaves the person blocked and the deletion can be retried. An account that is already gone counts as
     * deleted, which makes a retry safe.
     */
    /** Spec 014 FR-018: the person's regular sessions, one entry per device/browser. */
    public List<KeycloakSession> listUserSessions(String userId) {
        return readSessions(call(HttpMethod.GET, "/users/" + userId + "/sessions", null));
    }

    /** The phone app's offline sessions. The path needs the client's internal UUID, not its client id. */
    public List<KeycloakSession> listOfflineSessions(String userId, String clientUuid) {
        return readSessions(call(HttpMethod.GET, "/users/" + userId + "/offline-sessions/" + clientUuid, null));
    }

    /** Cached: the UUID never changes for a client within a realm. */
    public Optional<String> findClientUuid(String clientId) {
        String cached = clientUuidCache.get(clientId);
        if (cached != null) {
            return Optional.of(cached);
        }
        JsonNode clients = readTree(call(HttpMethod.GET, "/clients?clientId=" + clientId, null));
        if (clients.isEmpty()) {
            return Optional.empty();
        }
        String uuid = clients.get(0).path("id").asText();
        clientUuidCache.put(clientId, uuid);

        return Optional.of(uuid);
    }

    /** Ends one session (spec 014 FR-019). Offline sessions need {@code isOffline=true}. */
    public void deleteSession(String sessionId, boolean offline) {
        call(HttpMethod.DELETE, "/sessions/" + sessionId + (offline ? "?isOffline=true" : ""), null);
    }

    public void deleteUser(String userId) {
        try {
            call(HttpMethod.DELETE, "/users/" + userId, null);
        } catch (HttpClientErrorException.NotFound alreadyGone) {
            // Idempotent retry: the previous attempt deleted it.
        }
    }

    /**
     * Revokes a client's consent (and, with it, any {@code offline_access} grant that client
     * holds) for a user — spec 007 FR-013, research D12. A plain session logout does not revoke
     * offline tokens; this is the documented way to kill one. Throws
     * {@link HttpClientErrorException.NotFound} when the user never granted that client consent
     * — callers decide whether that is expected.
     */
    public void revokeConsent(String userId, String clientId) {
        call(HttpMethod.DELETE, "/users/" + userId + "/consents/" + clientId, null);
    }

    private String realmRole(String roleName) {
        return realmRoleCache.computeIfAbsent(roleName, name -> call(HttpMethod.GET, "/roles/" + name, null));
    }

    private KeycloakUser toUser(JsonNode userNode) {
        String id = userNode.path("id").asText();

        return new KeycloakUser(
                id,
                userNode.path("email").asText(null),
                userNode.path("firstName").asText(null),
                userNode.path("lastName").asText(null),
                Instant.ofEpochMilli(userNode.path("createdTimestamp").asLong(0)),
                userNode.path("enabled").asBoolean(true),
                realmRoleNames(id));
    }

    private List<String> realmRoleNames(String userId) {
        JsonNode rolesNode = readTree(call(HttpMethod.GET, "/users/" + userId + "/role-mappings/realm", null));
        List<String> roles = new ArrayList<>();
        for (JsonNode role : rolesNode) {
            roles.add(role.path("name").asText());
        }

        return roles;
    }

    private List<KeycloakSession> readSessions(String json) {
        JsonNode nodes = readTree(json);
        List<KeycloakSession> sessions = new ArrayList<>();
        for (JsonNode node : nodes) {
            List<String> clients = new ArrayList<>();
            node.path("clients").fieldNames().forEachRemaining(clients::add);
            sessions.add(new KeycloakSession(
                    node.path("id").asText(),
                    Instant.ofEpochMilli(node.path("start").asLong(0)),
                    Instant.ofEpochMilli(node.path("lastAccess").asLong(0)),
                    List.copyOf(clients)));
        }

        return sessions;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new KeycloakUnavailableException("Keycloak admin response unreadable", exception);
        }
    }

    private String call(HttpMethod method, String path, String body) {
        String token = accessToken();
        try {
            return doCall(method, path, body, token);
        } catch (HttpClientErrorException.Unauthorized unauthorized) {
            return retryAfterRefresh(method, path, body);
        } catch (HttpClientErrorException.NotFound notFound) {
            throw notFound;
        } catch (RestClientException exception) {
            throw new KeycloakUnavailableException("Keycloak admin " + method + " " + path + " failed", exception);
        }
    }

    private String retryAfterRefresh(HttpMethod method, String path, String body) {
        String refreshed = refreshToken();
        try {
            return doCall(method, path, body, refreshed);
        } catch (HttpClientErrorException.NotFound notFound) {
            throw notFound;
        } catch (RestClientException exception) {
            throw new KeycloakUnavailableException("Keycloak admin " + method + " " + path + " failed", exception);
        }
    }

    private String doCall(HttpMethod method, String path, String body, String token) {
        RestClient.RequestBodySpec spec = restClient.method(method)
                .uri(properties.serverUrl() + "/admin/realms/" + properties.realm() + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }

        return spec.retrieve().body(String.class);
    }

    private String accessToken() {
        Instant expiry = tokenExpiresAt;
        if (accessToken != null && expiry != null && Instant.now().isBefore(expiry)) {
            return accessToken;
        }

        return refreshToken();
    }

    private synchronized String refreshToken() {
        if (accessToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;
        }
        String form = "client_id=" + properties.adminClientId()
                + "&client_secret=" + properties.adminClientSecret()
                + "&grant_type=client_credentials";
        try {
            String body = restClient.post()
                    .uri(properties.serverUrl() + "/realms/" + properties.realm() + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body);
            String token = node.path("access_token").asText(null);
            if (!StringUtils.hasText(token)) {
                throw new KeycloakUnavailableException("Keycloak token response contained no access token");
            }
            long expiresIn = node.path("expires_in").asLong(60L);
            this.accessToken = token;
            this.tokenExpiresAt = Instant.now().plusSeconds(Math.max(10L, expiresIn - 10L));

            return token;
        } catch (KeycloakUnavailableException alreadyTranslated) {
            throw alreadyTranslated;
        } catch (RestClientException exception) {
            // Never log the form body: it carries the client secret.
            throw new KeycloakUnavailableException("Keycloak token request failed", exception);
        } catch (Exception exception) {
            throw new KeycloakUnavailableException("Keycloak token response unreadable", exception);
        }
    }

    /** Keycloak user as the Users page needs it (data-model.md AppUser view). */
    public record KeycloakUser(String id, String email, String firstName, String lastName, Instant createdAt,
            boolean enabled, List<String> realmRoles) {
    }

    /** One active session, with the clients it was opened for (spec 014 data-model §2). */
    public record KeycloakSession(String id, Instant startedAt, Instant lastAccessedAt, List<String> clientIds) {
    }
}
