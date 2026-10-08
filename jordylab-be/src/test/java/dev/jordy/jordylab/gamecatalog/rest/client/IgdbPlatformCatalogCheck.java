package dev.jordy.jordylab.gamecatalog.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import dev.jordy.jordylab.gamecatalog.domain.PlatformEntry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Re-fetches IGDB's {@code /platforms} and fails when any platform id in {@link PlatformCatalog} no longer carries the IGDB
 * name stored with it (spec 013 research B4). Run on demand with the dev credentials: {@code ./gradlew igdbCheck}. Excluded
 * from the normal test task because it calls a live service.
 */
@Tag("integration-igdb")
class IgdbPlatformCatalogCheck {

    @Test
    void everyIgdbPlatformIdStillCarriesItsStoredName() {
        String clientId = System.getenv("IGDB_CLIENT_ID");
        String clientSecret = System.getenv("IGDB_CLIENT_SECRET");
        Assumptions.assumeTrue(StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret),
                "IGDB_CLIENT_ID / IGDB_CLIENT_SECRET are not set");
        IgdbClient client = new IgdbClient(new ObjectMapper());
        client.clientId = clientId;
        client.clientSecret = clientSecret;
        client.apiBaseUrl = "https://api.igdb.com/v4";
        client.tokenUrl = "https://id.twitch.tv/oauth2/token";
        client.timeoutMs = 10_000;
        client.minIntervalMs = 260;
        client.init();
        List<PlatformEntry> entries = PlatformCatalog.all().stream().filter(entry -> entry.igdbPlatformId() != null).toList();

        Map<Long, String> live = client.fetchPlatformNames(entries.stream().map(PlatformEntry::igdbPlatformId).toList());

        assertThat(live).as("IGDB answered").isNotEmpty();
        for (PlatformEntry entry : entries) {
            assertThat(live.get(entry.igdbPlatformId())).as("IGDB name of platform id %s (%s)", entry.igdbPlatformId(),
                    entry.name()).isEqualTo(entry.igdbName());
        }
    }
}
