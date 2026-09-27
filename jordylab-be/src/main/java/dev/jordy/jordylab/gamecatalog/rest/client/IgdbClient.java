package dev.jordy.jordylab.gamecatalog.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * IGDB (Twitch) client for structured multiplayer data on EmuDeck/ROM games and as a fallback
 * when Steam has no category data. Uses OAuth client-credentials; the token is cached in memory
 * and refreshed on expiry or a 401. Neither the client secret nor the token is ever logged.
 * Degrades gracefully (empty results) when unconfigured or on any failure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IgdbClient {

    private static final String DEFAULT_API_BASE_URL = "https://api.igdb.com/v4";
    private static final String DEFAULT_TOKEN_URL = "https://id.twitch.tv/oauth2/token";
    private static final int MAX_SEARCH_RESULTS = 10;
    private static final int MAX_MODE_ROWS = 500;

    @Value("${IGDB_CLIENT_ID:}")
    String clientId;

    @Value("${IGDB_CLIENT_SECRET:}")
    String clientSecret;

    @Value("${jordylab.gamecatalog.igdb.api-base-url:" + DEFAULT_API_BASE_URL + "}")
    String apiBaseUrl;

    @Value("${jordylab.gamecatalog.igdb.token-url:" + DEFAULT_TOKEN_URL + "}")
    String tokenUrl;

    @Value("${jordylab.gamecatalog.igdb.timeout-ms:5000}")
    int timeoutMs;

    private final ObjectMapper objectMapper;

    private RestClient restClient;
    private volatile String accessToken;
    private volatile Instant tokenExpiresAt;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    /** IGDB game ids whose name matches the (already cleaned) title exactly, in search order. */
    public List<Long> findGameIdsByTitle(String title) {
        if (!isConfigured() || !StringUtils.hasText(title)) {
            return List.of();
        }
        String query = "search \"" + escapeQuery(title) + "\"; fields name; "
                + "where version_parent = null & game_type = 0; limit " + MAX_SEARCH_RESULTS + ";";
        JsonNode response = post("/games", query);
        if (response == null || !response.isArray()) {
            return List.of();
        }
        String normalized = normalizeTitle(title);
        List<Long> ids = new ArrayList<>();
        for (JsonNode game : response) {
            if (normalized.equals(normalizeTitle(game.path("name").asText(null)))) {
                ids.add(game.path("id").asLong());
            }
        }

        return ids;
    }

    /**
     * Resolves structured multiplayer data for a title. IGDB often holds several same-named
     * entries (regions, re-releases) where only some carry {@code multiplayer_modes} data, so all
     * exact-name matches are queried in one batch and the first with data wins.
     */
    public Optional<MultiplayerMode> resolveMultiplayerMode(String title) {
        List<Long> igdbGameIds = findGameIdsByTitle(title);
        if (igdbGameIds.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, MultiplayerMode> modes = fetchMultiplayerModes(igdbGameIds);
        for (Long igdbGameId : igdbGameIds) {
            MultiplayerMode mode = modes.get(igdbGameId);
            if (mode != null) {
                return Optional.of(mode);
            }
        }

        return Optional.empty();
    }

    /**
     * Fetches multiplayer modes for the given IGDB game ids in one batched request, aggregating
     * across the per-platform rows (OR for booleans, max for player counts).
     */
    public Map<Long, MultiplayerMode> fetchMultiplayerModes(List<Long> igdbGameIds) {
        if (!isConfigured() || igdbGameIds == null || igdbGameIds.isEmpty()) {
            return Map.of();
        }
        String ids = igdbGameIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String query = "fields game,platform,offlinecoop,offlinecoopmax,offlinemax,lancoop,campaigncoop,"
                + "onlinecoop,onlinemax,splitscreen,splitscreenonline; where game = (" + ids + "); limit "
                + MAX_MODE_ROWS + ";";
        JsonNode response = post("/multiplayer_modes", query);
        if (response == null || !response.isArray()) {
            return Map.of();
        }
        Map<Long, MultiplayerMode> modes = new HashMap<>();
        for (JsonNode row : response) {
            long gameId = row.path("game").asLong(-1);
            if (gameId < 0) {
                continue;
            }
            boolean localMultiplayer = row.path("offlinecoop").asBoolean(false) || row.path("lancoop").asBoolean(false)
                    || row.path("splitscreen").asBoolean(false);
            // offlinemax also counts alternating/turn-based play, so only trust a player count when
            // there is an actual simultaneous local mode.
            Integer maxLocalPlayers = localMultiplayer
                    ? maxOf(row.path("offlinemax").asInt(0), row.path("offlinecoopmax").asInt(0))
                    : null;
            MultiplayerMode mode = new MultiplayerMode(localMultiplayer,
                    row.path("splitscreen").asBoolean(false),
                    row.path("onlinecoop").asBoolean(false),
                    maxLocalPlayers);
            modes.merge(gameId, mode, MultiplayerMode::merge);
        }

        return modes;
    }

    private JsonNode post(String path, String query) {
        String token = accessToken();
        if (token == null) {
            return null;
        }
        try {
            return execute(path, query, token);
        } catch (HttpClientErrorException.Unauthorized unauthorized) {
            // Token expired mid-flight: refresh once and retry.
            accessToken = null;
            tokenExpiresAt = null;
            String refreshed = accessToken();
            if (refreshed == null) {
                return null;
            }
            try {
                return execute(path, query, refreshed);
            } catch (RuntimeException retryFailure) {
                log.warn("IGDB {} retry failed: {}", path, retryFailure.getClass().getSimpleName());

                return null;
            }
        }
    }

    private JsonNode execute(String path, String query, String token) {
        try {
            String body = restClient.post()
                    .uri(apiBaseUrl + path)
                    .header("Client-ID", clientId)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(query)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readTree(body);
        } catch (HttpClientErrorException.Unauthorized unauthorized) {
            // Propagated to post(), which refreshes the token and retries exactly once.
            throw unauthorized;
        } catch (RestClientException exception) {
            log.warn("IGDB {} call failed: {}", path, exception.getClass().getSimpleName());

            return null;
        } catch (Exception exception) {
            log.warn("IGDB {} response unreadable: {}", path, exception.getClass().getSimpleName());

            return null;
        }
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
        URI uri = UriComponentsBuilder.fromUriString(tokenUrl)
                .queryParam("client_id", clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("grant_type", "client_credentials")
                .build()
                .encode()
                .toUri();
        try {
            String body = restClient.post().uri(uri).retrieve().body(String.class);
            JsonNode node = objectMapper.readTree(body);
            String token = node.path("access_token").asText(null);
            if (!StringUtils.hasText(token)) {
                log.warn("IGDB token response contained no access token");

                return null;
            }
            long expiresIn = node.path("expires_in").asLong(3600L);
            this.accessToken = token;
            this.tokenExpiresAt = Instant.now().plusSeconds(Math.max(60L, expiresIn - 60L));

            return token;
        } catch (RestClientException exception) {
            // Never log the URI: it carries the client secret.
            log.warn("IGDB token request failed: {}", exception.getClass().getSimpleName());

            return null;
        } catch (Exception exception) {
            log.warn("IGDB token response unreadable: {}", exception.getClass().getSimpleName());

            return null;
        }
    }

    private String escapeQuery(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }

        return title.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private Integer maxOf(int first, int second) {
        int max = Math.max(first, second);

        return max > 0 ? max : null;
    }

    /** Aggregated multiplayer facts for one IGDB game (across its platform rows). */
    public record MultiplayerMode(boolean localMultiplayer, boolean splitScreen, boolean onlineMultiplayer,
            Integer maxLocalPlayers) {

        MultiplayerMode merge(MultiplayerMode other) {
            Integer mergedMax = max(maxLocalPlayers, other.maxLocalPlayers);

            return new MultiplayerMode(localMultiplayer || other.localMultiplayer, splitScreen || other.splitScreen,
                    onlineMultiplayer || other.onlineMultiplayer, mergedMax);
        }

        private static Integer max(Integer first, Integer second) {
            if (first == null) {
                return second;
            }
            if (second == null) {
                return first;
            }

            return Math.max(first, second);
        }
    }
}
