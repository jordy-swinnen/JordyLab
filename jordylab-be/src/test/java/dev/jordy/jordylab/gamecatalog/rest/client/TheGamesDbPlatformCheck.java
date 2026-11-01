package dev.jordy.jordylab.gamecatalog.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Re-fetches TheGamesDB's {@code /Platforms} and fails when a console in the client's map no longer carries the id and name
 * stored with it (spec 014 research R4). Run on demand with the key in the environment: {@code ./gradlew theGamesDbCheck}.
 * One call; excluded from the normal test task because it calls a live service and spends one lookup of the monthly
 * allowance. The request address carries the key, so it is never printed or put in a message.
 */
@Tag("integration-thegamesdb")
class TheGamesDbPlatformCheck {

    @Test
    void everyConsoleInTheMapStillCarriesItsStoredIdAndName() throws Exception {
        String apiKey = System.getenv("THEGAMESDB_API_KEY");
        Assumptions.assumeTrue(StringUtils.hasText(apiKey), "THEGAMESDB_API_KEY is not set");
        URI uri = UriComponentsBuilder.fromUriString("https://api.thegamesdb.net/v1/Platforms")
                .queryParam("apikey", "{apikey}").encode().buildAndExpand(Map.of("apikey", apiKey)).toUri();
        String body = RestClient.create().get().uri(uri).header("User-Agent", "JordyLab/1.0 (+https://jordylab.be)")
                .retrieve().body(String.class);

        JsonNode platforms = new ObjectMapper().readTree(body).path("data").path("platforms");

        assertThat(platforms.isEmpty()).as("TheGamesDB answered with a platform list").isFalse();
        TheGamesDbClient.platforms().forEach((console, platform) -> assertThat(
                platforms.path(String.valueOf(platform.id())).path("name").asText(null))
                .as("TheGamesDB name of platform id %s (%s)", platform.id(), console).isEqualTo(platform.name()));
    }
}
