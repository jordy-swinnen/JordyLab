package dev.jordy.jordylab.gamecatalog.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.util.TextSanitizer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the owned Steam library through the documented {@code IPlayerService/GetOwnedGames}
 * endpoint, using a Web API key and the account id from configuration (FR-008). Churns
 * independent of any host being online. The key travels as a query parameter, so the request
 * URI is never logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamOwnedGamesClient {

    private static final String BASE_URL = "https://api.steampowered.com";

    @Value("${jordylab.gamecatalog.library.owned-games-base-url:" + BASE_URL + "}")
    String baseUrl;

    @Value("${STEAM_WEB_API_KEY:}")
    String apiKey;

    @Value("${STEAM_ID:}")
    String steamId;

    private final GameCatalogProperties properties;
    private final ObjectMapper objectMapper;

    private RestClient restClient;

    @PostConstruct
    void init() {
        int timeoutMs = (int) properties.library().callTimeoutMs();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(steamId);
    }

    /**
     * Fetches the owned library. Empty means the call failed (or no account is configured); a
     * present (possibly empty) list is a valid response.
     */
    public Optional<List<OwnedGame>> fetchOwnedGames() {
        if (!isConfigured()) {
            log.debug("Steam owned-library sync skipped: STEAM_WEB_API_KEY / STEAM_ID not configured");

            return Optional.empty();
        }
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("IPlayerService", "GetOwnedGames", "v1")
                .queryParam("key", apiKey)
                .queryParam("steamid", steamId)
                .queryParam("include_appinfo", "true")
                .queryParam("include_played_free_games", "true")
                .queryParam("format", "json")
                .build()
                .encode()
                .toUri();
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);

            return parse(body);
        } catch (RestClientException exception) {
            // Never log the URI: it carries the API key.
            log.warn("Steam GetOwnedGames call failed: {}", exception.getClass().getSimpleName());

            return Optional.empty();
        }
    }

    private Optional<List<OwnedGame>> parse(String body) {
        try {
            JsonNode response = objectMapper.readTree(body).path("response");
            JsonNode games = response.path("games");
            if (games.isMissingNode()) {
                // Documented shape for a private/empty library: response has no games array.
                return Optional.of(List.of());
            }
            if (!games.isArray()) {
                return Optional.empty();
            }
            List<OwnedGame> owned = new ArrayList<>();
            for (JsonNode game : games) {
                if (!game.hasNonNull("appid")) {
                    continue;
                }
                String appId = String.valueOf(game.get("appid").asLong());
                owned.add(new OwnedGame(appId, TextSanitizer.sanitizeTitle(game.path("name").asText(null))));
            }

            return Optional.of(owned);
        } catch (Exception exception) {
            log.warn("Steam GetOwnedGames response unreadable: {}", exception.getClass().getSimpleName());

            return Optional.empty();
        }
    }

    public record OwnedGame(String appId, String name) {
    }
}
