package dev.jordy.jordylab.gamecatalog.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads deterministic metadata from Steam's public store appdetails endpoint (keyless).
 * One fetch per game, never on a request path.
 */
@Slf4j
@Component
public class SteamAppDetailsClient {

    private static final String STORE_APP_DETAILS_BASE_URL = "https://store.steampowered.com";
    private static final int MAX_GENRES_LENGTH = 200;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MIN_RELEASE_YEAR = 1950;
    private static final int MAX_RELEASE_YEAR = 2028;
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");

    private final RestClient restClient;
    private final String storeBaseUrl;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public SteamAppDetailsClient(GameCatalogProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, STORE_APP_DETAILS_BASE_URL);
    }

    SteamAppDetailsClient(GameCatalogProperties properties, ObjectMapper objectMapper, String storeBaseUrl) {
        int timeoutMs = (int) properties.artwork().lookupTimeoutMs();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.storeBaseUrl = storeBaseUrl;
        this.objectMapper = objectMapper;
    }

    public Optional<SteamMetadata> fetch(String steamAppId) {
        if (!StringUtils.hasText(steamAppId)) {
            return Optional.empty();
        }
        URI uri = UriComponentsBuilder.fromUriString(storeBaseUrl)
                .pathSegment("api", "appdetails")
                .queryParam("appids", steamAppId)
                .queryParam("filters", "basic")
                .build()
                .encode()
                .toUri();
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);

            return parse(steamAppId, body);
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
        JsonNode appNode = root.get(steamAppId);
        if (appNode == null || !appNode.path("success").asBoolean(false)) {
            return Optional.empty();
        }
        JsonNode data = appNode.get("data");
        if (data == null || data.isNull()) {
            return Optional.empty();
        }

        String genres = extractGenres(data.get("genres"));
        String developer = firstMember(data.get("developers"));
        String publisher = firstMember(data.get("publishers"));
        Integer releaseYear = extractReleaseYear(data.path("release_date").path("date").asText(null));

        return Optional.of(new SteamMetadata(genres, developer, publisher, releaseYear));
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

    public record SteamMetadata(String genres, String developer, String publisher, Integer releaseYear) {
    }
}
