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
}
