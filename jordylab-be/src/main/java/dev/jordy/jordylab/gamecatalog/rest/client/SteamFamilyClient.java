package dev.jordy.jordylab.gamecatalog.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.util.TextSanitizer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the Steam Family shared library through the undocumented {@code IFamilyGroupsService}
 * endpoints, authenticated with a short-lived user access token supplied per call (FR-010).
 * The token is used only for the request and never stored, logged or returned (FR-011).
 *
 * <p>The response field names are provisional until confirmed against a real, sanitised capture;
 * an unexpected shape raises {@link SteamFamilyException} so the run fails without touching data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamFamilyClient {

    private static final String BASE_URL = "https://api.steampowered.com";
    private static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    private static final String UNKNOWN_RESPONSE = "UNKNOWN_RESPONSE";

    @Value("${jordylab.gamecatalog.library.family-base-url:" + BASE_URL + "}")
    String baseUrl;

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

    public List<FamilyGame> fetchSharedLibrary(String accessToken) {
        String groupId = fetchFamilyGroupId(accessToken);

        return fetchSharedApps(accessToken, groupId);
    }

    private String fetchFamilyGroupId(String accessToken) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("IFamilyGroupsService", "GetFamilyGroupForUser", "v1")
                .queryParam("access_token", accessToken)
                .queryParam("format", "json")
                .build()
                .encode()
                .toUri();
        JsonNode response = get(uri, accessToken).path("response");
        String groupId = firstText(response, "family_groupid", "family_group_id", "steamid");
        if (!StringUtils.hasText(groupId)) {
            throw new SteamFamilyException(UNKNOWN_RESPONSE, "Family group id missing from response");
        }

        return groupId;
    }

    private List<FamilyGame> fetchSharedApps(String accessToken, String groupId) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("IFamilyGroupsService", "GetSharedLibraryApps", "v1")
                .queryParam("access_token", accessToken)
                .queryParam("family_groupid", groupId)
                .queryParam("include_own", "true")
                .queryParam("format", "json")
                .build()
                .encode()
                .toUri();
        JsonNode apps = get(uri, accessToken).path("response").path("apps");
        if (!apps.isArray()) {
            throw new SteamFamilyException(UNKNOWN_RESPONSE, "Shared library apps array missing from response");
        }
        List<FamilyGame> games = new ArrayList<>();
        for (JsonNode app : apps) {
            if (!app.hasNonNull("appid")) {
                continue;
            }
            games.add(new FamilyGame(String.valueOf(app.get("appid").asLong()),
                    TextSanitizer.sanitizeTitle(app.path("name").asText(null)),
                    app.path("exclude_reason").asInt(0),
                    ownerIds(app.get("owner_steamids"))));
        }

        return games;
    }

    private JsonNode get(URI uri, String accessToken) {
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!root.has("response")) {
                throw new SteamFamilyException(UNKNOWN_RESPONSE, "Family response missing 'response'");
            }

            return root;
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED
                    || exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new SteamFamilyException(TOKEN_EXPIRED, "Steam rejected the family access token");
            }
            throw new SteamFamilyException(UNKNOWN_RESPONSE, "Family endpoint returned " + exception.getStatusCode());
        } catch (SteamFamilyException exception) {
            throw exception;
        } catch (RestClientException exception) {
            // Never log the URI: it carries the access token.
            throw new SteamFamilyException(UNKNOWN_RESPONSE, "Family endpoint call failed");
        } catch (Exception exception) {
            throw new SteamFamilyException(UNKNOWN_RESPONSE, "Family response unreadable");
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }

        return null;
    }

    private List<String> ownerIds(JsonNode ownersNode) {
        List<String> owners = new ArrayList<>();
        if (ownersNode == null || !ownersNode.isArray()) {
            return owners;
        }
        for (JsonNode owner : ownersNode) {
            String id = owner.asText(null);
            if (StringUtils.hasText(id)) {
                owners.add(id);
            }
        }

        return owners;
    }

    public record FamilyGame(String appId, String name, int excludeReason, List<String> ownerIds) {

        public boolean isShareable() {
            return excludeReason == 0;
        }
    }
}
