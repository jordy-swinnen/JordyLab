package dev.jordy.jordylab.gamecatalog.rest.client;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.util.LibretroNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

@Slf4j
@Component
public class ArtworkLookupClient {

    private static final String STEAM_CDN_BASE_URL = "https://cdn.cloudflare.steamstatic.com/steam/apps";
    private static final String LIBRETRO_BASE_URL = "https://raw.githubusercontent.com/libretro/libretro-thumbnails/master";

    private static final String LIBRETRO_COVER_MEDIA = "Named_Boxarts";
    private static final String LIBRETRO_BANNER_MEDIA = "Named_Snaps";

    private final RestClient restClient;
    private final String steamCdnBaseUrl;
    private final String libretroBaseUrl;

    @org.springframework.beans.factory.annotation.Autowired
    public ArtworkLookupClient(GameCatalogProperties properties) {
        this(properties, STEAM_CDN_BASE_URL, LIBRETRO_BASE_URL);
    }

    ArtworkLookupClient(GameCatalogProperties properties, String steamCdnBaseUrl, String libretroBaseUrl) {
        int timeoutMs = (int) properties.artwork().lookupTimeoutMs();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.steamCdnBaseUrl = steamCdnBaseUrl;
        this.libretroBaseUrl = libretroBaseUrl;
    }

    /**
     * Portrait, card-fitted cover art. Steam uses the official library portrait (600x900);
     * ROMs use libretro box art.
     */
    public Optional<String> findCoverArtworkUrl(SourceType sourceType, String platform, String steamAppId,
            String title) {
        if (sourceType == SourceType.STEAM) {
            return findSteamAsset(steamAppId, "library_600x900.jpg", "library_600x900_2x.jpg");
        }

        return findLibretroAsset(platform, LIBRETRO_COVER_MEDIA, title);
    }

    /**
     * Wide banner art for the detail page. Steam uses the library hero (1920x620 family);
     * ROMs use libretro in-game snapshots.
     */
    public Optional<String> findBannerArtworkUrl(SourceType sourceType, String platform, String steamAppId,
            String title) {
        if (sourceType == SourceType.STEAM) {
            return findSteamAsset(steamAppId, "library_hero.jpg");
        }

        return findLibretroAsset(platform, LIBRETRO_BANNER_MEDIA, title);
    }

    private Optional<String> findSteamAsset(String steamAppId, String... candidates) {
        if (steamAppId == null || steamAppId.isBlank()) {
            return Optional.empty();
        }
        for (String candidate : candidates) {
            String url = steamCdnBaseUrl + "/" + steamAppId + "/" + candidate;
            if (probeExists(URI.create(url))) {
                return Optional.of(url);
            }
        }

        return Optional.empty();
    }

    private Optional<String> findLibretroAsset(String platform, String media, String title) {
        String repo = PlatformCatalog.entryFor(platform).libretroRepository();
        if (repo == null) {
            return Optional.empty();
        }
        for (String name : LibretroNames.variants(title)) {
            URI candidateUri = UriComponentsBuilder.fromUriString(libretroBaseUrl)
                    .pathSegment(repo, media, name + ".png")
                    .build()
                    .encode()
                    .toUri();
            if (probeExists(candidateUri)) {
                return Optional.of(candidateUri.toString());
            }
        }

        return Optional.empty();
    }

    private boolean probeExists(URI uri) {
        try {
            restClient.head().uri(uri).retrieve().toBodilessEntity();

            return true;
        } catch (RestClientException exception) {
            log.debug("Artwork probe missed for {}: {}", uri, exception.getMessage());

            return false;
        }
    }
}
