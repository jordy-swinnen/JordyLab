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
    private static final String IMAGE_CDN_BASE_URL = "https://images.igdb.com/igdb/image/upload";
    private static final int MAX_SEARCH_RESULTS = 10;
    private static final int MAX_MODE_ROWS = 500;
    private static final long NINTENDO_SWITCH_PLATFORM_ID = 130L;
    /**
     * IGDB {@code game_type}s a Switch library holds: main game (0), standalone expansion (4), remake (8),
     * remaster (9), expanded game (10) and port (11). Main games alone missed Switch staples such as
     * Mario Kart 8 Deluxe (expanded game) and every port (spec 011 BUG-042); DLC, bundles, mods, episodes,
     * seasons, packs and updates stay out.
     */
    static final String SWITCH_GAME_TYPES = "(0,4,8,9,10,11)";

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

    /**
     * Minimum gap between two IGDB calls. IGDB allows 4 requests per second per client and answers 429 beyond that;
     * a Switch bulk add (spec 009 US3) makes one search per pasted line, so calls are paced here, for every caller.
     */
    @Value("${jordylab.gamecatalog.igdb.min-interval-ms:260}")
    long minIntervalMs;

    private final ObjectMapper objectMapper;

    private RestClient restClient;
    private volatile String accessToken;
    private volatile Instant tokenExpiresAt;
    private long lastCallNanos;

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
     * Searches IGDB for Nintendo Switch titles matching the query. Returns the first
     * {@value #MAX_SEARCH_RESULTS} matches with metadata and cover/banner URLs; empty if the
     * client is unconfigured or the call fails.
     */
    public List<SwitchSearchResult> searchSwitchGames(String query) {
        if (!isConfigured() || !StringUtils.hasText(query)) {
            return List.of();
        }
        String apicalypse = "search \"" + escapeQuery(query) + "\"; "
                + "fields name,first_release_date,genres.name,involved_companies.company.name,"
                + "cover.image_id,artworks.image_id; "
                + "where platforms = (" + NINTENDO_SWITCH_PLATFORM_ID + ") & version_parent = null & game_type = "
                + SWITCH_GAME_TYPES + "; "
                + "limit " + MAX_SEARCH_RESULTS + ";";
        JsonNode response = post("/games", apicalypse);
        if (response == null || !response.isArray()) {
            return List.of();
        }

        List<SwitchSearchResult> results = new ArrayList<>();
        for (JsonNode game : response) {
            SwitchSearchResult result = parseSwitchGame(game);
            if (result != null) {
                results.add(result);
            }
        }

        return results;
    }

    /**
     * Fetches full Switch details for an IGDB game id, including aggregated multiplayer modes
     * and cover/banner URLs.
     */
    public Optional<SwitchGameDetails> fetchSwitchGameDetails(long igdbGameId) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        String apicalypse = "fields name,first_release_date,genres.name,involved_companies.company.name,"
                + "cover.image_id,artworks.image_id; where id = " + igdbGameId + ";";
        JsonNode response = post("/games", apicalypse);
        if (response == null || !response.isArray() || response.isEmpty()) {
            return Optional.empty();
        }

        SwitchSearchResult base = parseSwitchGame(response.get(0));
        if (base == null) {
            return Optional.empty();
        }
        Map<Long, MultiplayerMode> modes = fetchMultiplayerModes(List.of(igdbGameId));
        MultiplayerMode multiplayerMode = modes.get(igdbGameId);

        return Optional.of(new SwitchGameDetails(base.igdbGameId(), base.title(), base.releaseYear(),
                base.genres(), base.developer(), base.coverUrl(), base.bannerUrl(), multiplayerMode));
    }

    /**
     * Builds an IGDB image CDN URL for the given image id and size suffix.
     */
    public String buildImageUrl(String imageId, ImageSize size) {
        if (!StringUtils.hasText(imageId)) {
            return null;
        }

        return IMAGE_CDN_BASE_URL + "/t_" + size.suffix + "/" + imageId + ".jpg";
    }

    private SwitchSearchResult parseSwitchGame(JsonNode game) {
        long igdbGameId = game.path("id").asLong(-1);
        String title = game.path("name").asText(null);
        if (igdbGameId < 0 || !StringUtils.hasText(title)) {
            return null;
        }

        Integer releaseYear = parseReleaseYear(game.path("first_release_date"));
        List<String> genres = parseNames(game.path("genres"));
        String developer = parseFirstCompanyName(game.path("involved_companies"));
        String coverImageId = game.path("cover").path("image_id").asText(null);
        String bannerImageId = firstArtworkImageId(game.path("artworks"));

        return new SwitchSearchResult(igdbGameId, title, releaseYear, genres, developer,
                buildImageUrl(coverImageId, ImageSize.COVER_BIG),
                buildImageUrl(bannerImageId, ImageSize.SCREENSHOT_BIG));
    }

    private Integer parseReleaseYear(JsonNode timestampNode) {
        long timestamp = timestampNode.asLong(0);
        if (timestamp == 0) {
            return null;
        }

        return java.time.Instant.ofEpochSecond(timestamp).atZone(java.time.ZoneOffset.UTC).getYear();
    }

    private List<String> parseNames(JsonNode array) {
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (JsonNode element : array) {
            String name = element.path("name").asText(null);
            if (StringUtils.hasText(name)) {
                names.add(name);
            }
        }

        return names;
    }

    private String parseFirstCompanyName(JsonNode array) {
        if (array == null || !array.isArray() || array.isEmpty()) {
            return null;
        }

        return array.get(0).path("company").path("name").asText(null);
    }

    private String firstArtworkImageId(JsonNode array) {
        if (array == null || !array.isArray() || array.isEmpty()) {
            return null;
        }

        return array.get(0).path("image_id").asText(null);
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

    private synchronized void paceCalls() {
        long waitNanos = lastCallNanos + minIntervalMs * 1_000_000L - System.nanoTime();
        if (lastCallNanos != 0 && waitNanos > 0) {
            try {
                Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallNanos = System.nanoTime();
    }

    private JsonNode execute(String path, String query, String token) {
        paceCalls();
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

    /** Search result for a Nintendo Switch game on IGDB. */
    public record SwitchSearchResult(long igdbGameId, String title, Integer releaseYear, List<String> genres,
            String developer, String coverUrl, String bannerUrl) {
    }

    /** Full details for a Switch game, including aggregated multiplayer facts. */
    public record SwitchGameDetails(long igdbGameId, String title, Integer releaseYear, List<String> genres,
            String developer, String coverUrl, String bannerUrl, MultiplayerMode multiplayerMode) {
    }

    /** IGDB image CDN size suffixes. */
    public enum ImageSize {
        COVER_BIG("cover_big"),
        SCREENSHOT_BIG("screenshot_big");

        private final String suffix;

        ImageSize(String suffix) {
            this.suffix = suffix;
        }
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
