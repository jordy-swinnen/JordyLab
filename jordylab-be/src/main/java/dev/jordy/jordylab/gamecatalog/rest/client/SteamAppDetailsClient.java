package dev.jordy.jordylab.gamecatalog.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads deterministic metadata from Steam's public store appdetails endpoint (keyless).
 * The filter set must include {@code genres,categories,developers,publishers,release_date} —
 * {@code basic} alone does not return them. HTTP 429 raises {@link SteamRateLimitedException}
 * so the caller can pause the batch instead of recording a game failure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamAppDetailsClient {

    private static final String STORE_APP_DETAILS_BASE_URL = "https://store.steampowered.com";
    private static final String FILTERS = "basic,genres,categories,developers,publishers,release_date";
    private static final int MAX_GENRES_LENGTH = 200;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;
    private static final int MIN_RELEASE_YEAR = 1950;
    private static final int MAX_RELEASE_YEAR = 2028;
    private static final String CATEGORY_SINGLE_PLAYER = "Single-player";
    private static final String CATEGORY_LOCAL_COOP = "Local Co-op";
    private static final String CATEGORY_SPLIT_SCREEN_PREFIX = "Shared/Split Screen";
    // Matched on description text, not numeric ids — Steam reuses/shifts category ids over time.
    private static final Set<String> CATEGORY_ONLINE = Set.of(
            "Multi-player", "Cross-Platform Multiplayer", "Co-op", "Online Co-op", "Online PvP", "PvP");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");

    @Value("${jordylab.gamecatalog.metadata.store-base-url:" + STORE_APP_DETAILS_BASE_URL + "}")
    String storeBaseUrl;

    private final GameCatalogProperties properties;
    private final ObjectMapper objectMapper;

    private RestClient restClient;

    @PostConstruct
    void init() {
        int timeoutMs = (int) properties.artwork().lookupTimeoutMs();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public Optional<SteamMetadata> fetch(String steamAppId) {
        if (!StringUtils.hasText(steamAppId)) {
            return Optional.empty();
        }
        URI uri = UriComponentsBuilder.fromUriString(storeBaseUrl)
                .pathSegment("api", "appdetails")
                .queryParam("appids", steamAppId)
                .queryParam("filters", FILTERS)
                .build()
                .encode()
                .toUri();
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);

            return parse(steamAppId, body);
        } catch (HttpClientErrorException.TooManyRequests rateLimited) {
            throw new SteamRateLimitedException("Steam store rate limit hit for appdetails");
        } catch (RestClientException exception) {
            log.debug("Steam appdetails fetch failed for {}: {}", steamAppId, exception.getMessage());

            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Steam appdetails response unreadable for {}: {}", steamAppId, exception.getMessage());

            return Optional.empty();
        }
    }

    private Optional<SteamMetadata> parse(String steamAppId, String body) throws Exception {
        if (!StringUtils.hasText(body)) {
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = selectData(root, steamAppId);
        if (data == null) {
            return Optional.empty();
        }

        String type = data.path("type").asText(null);
        String genres = extractGenres(data.get("genres"));
        String developer = firstMember(data.get("developers"));
        String publisher = firstMember(data.get("publishers"));
        Integer releaseYear = extractReleaseYear(data.path("release_date").path("date").asText(null));
        String shortDescription = boundedText(data.path("short_description").asText(null), MAX_DESCRIPTION_LENGTH);

        return Optional.of(new SteamMetadata(genres, developer, publisher, releaseYear, shortDescription, type,
                multiplayerFacts(data.get("categories"))));
    }

    /**
     * Derives multiplayer facts from the categories array by matching on description text (ids are
     * reused/shifted over time). {@code categoriesPresent} distinguishes "checked, no categories"
     * from "no category data at all" so the caller can fall back to IGDB.
     */
    private MultiplayerFacts multiplayerFacts(JsonNode categories) {
        if (categories == null || !categories.isArray() || categories.isEmpty()) {
            return new MultiplayerFacts(null, null, null, null, false);
        }
        boolean splitScreen = hasCategoryPrefix(categories, CATEGORY_SPLIT_SCREEN_PREFIX);
        boolean localMultiplayer = splitScreen || hasCategoryDescription(categories, CATEGORY_LOCAL_COOP);
        boolean singlePlayer = hasCategoryDescription(categories, CATEGORY_SINGLE_PLAYER);
        boolean onlineMultiplayer = hasAnyCategoryDescription(categories, CATEGORY_ONLINE);

        return new MultiplayerFacts(singlePlayer, onlineMultiplayer, localMultiplayer, splitScreen, true);
    }

    private boolean hasCategoryDescription(JsonNode categories, String description) {
        for (JsonNode category : categories) {
            if (description.equals(category.path("description").asText(null))) {
                return true;
            }
        }

        return false;
    }

    private boolean hasAnyCategoryDescription(JsonNode categories, Set<String> descriptions) {
        for (JsonNode category : categories) {
            if (descriptions.contains(category.path("description").asText(null))) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCategoryPrefix(JsonNode categories, String prefix) {
        for (JsonNode category : categories) {
            String description = category.path("description").asText(null);
            if (description != null && description.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Steam does not reliably key the appdetails response by the requested app id (observed live:
     * {@code appids=620} returns a payload keyed {@code 323180}). The authoritative id is
     * {@code data.steam_appid}, so match on that and fall back to the first successful entry.
     */
    private JsonNode selectData(JsonNode root, String steamAppId) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode firstSuccessful = null;
        Iterator<Map.Entry<String, JsonNode>> entries = root.fields();
        while (entries.hasNext()) {
            JsonNode entry = entries.next().getValue();
            if (!entry.path("success").asBoolean(false)) {
                continue;
            }
            JsonNode data = entry.get("data");
            if (data == null || data.isNull()) {
                continue;
            }
            if (steamAppId.equals(data.path("steam_appid").asText(null))) {
                return data;
            }
            if (firstSuccessful == null) {
                firstSuccessful = data;
            }
        }

        return firstSuccessful;
    }

    private String extractGenres(JsonNode genresNode) {
        if (genresNode == null || !genresNode.isArray()) {
            return null;
        }
        StringBuilder genres = new StringBuilder();
        for (JsonNode genre : genresNode) {
            String description = genre.path("description").asText(null);
            if (!StringUtils.hasText(description)) {
                continue;
            }
            int additionalLength = genres.isEmpty() ? description.length() : description.length() + 2;
            if (genres.length() + additionalLength > MAX_GENRES_LENGTH) {
                break;
            }
            if (!genres.isEmpty()) {
                genres.append(", ");
            }
            genres.append(description);
        }

        return genres.isEmpty() ? null : genres.toString();
    }

    private String firstMember(JsonNode membersNode) {
        if (membersNode == null || !membersNode.isArray() || membersNode.isEmpty()) {
            return null;
        }
        String member = membersNode.get(0).asText(null);
        if (!StringUtils.hasText(member)) {
            return null;
        }

        return member.length() <= MAX_NAME_LENGTH ? member : member.substring(0, MAX_NAME_LENGTH);
    }

    private String boundedText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private Integer extractReleaseYear(String releaseDate) {
        if (!StringUtils.hasText(releaseDate)) {
            return null;
        }
        Matcher matcher = YEAR_PATTERN.matcher(releaseDate);
        Integer year = null;
        while (matcher.find()) {
            int candidate = Integer.parseInt(matcher.group(1));
            if (candidate >= MIN_RELEASE_YEAR && candidate <= MAX_RELEASE_YEAR) {
                year = candidate;
            }
        }

        return year;
    }

    public record SteamMetadata(String genres, String developer, String publisher, Integer releaseYear,
            String shortDescription, String type, MultiplayerFacts multiplayer) {

        public boolean isGame() {
            return "game".equals(type);
        }
    }

    /** Multiplayer facts derived from Steam store categories. {@code categoriesPresent=false} means no data. */
    public record MultiplayerFacts(Boolean singlePlayer, Boolean onlineMultiplayer, Boolean localMultiplayer,
            Boolean splitScreen, boolean categoriesPresent) {
    }
}
