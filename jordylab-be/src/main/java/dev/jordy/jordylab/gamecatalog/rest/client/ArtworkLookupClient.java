package dev.jordy.jordylab.gamecatalog.rest.client;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class ArtworkLookupClient {

    private static final String STEAM_CDN_BASE_URL = "https://cdn.cloudflare.steamstatic.com/steam/apps";
    private static final String LIBRETRO_BASE_URL = "https://raw.githubusercontent.com/libretro/libretro-thumbnails/master";

    private static final String LIBRETRO_COVER_MEDIA = "Named_Boxarts";
    private static final String LIBRETRO_BANNER_MEDIA = "Named_Snaps";

    private static final Map<String, String> LIBRETRO_REPOS_BY_PLATFORM = Map.ofEntries(
            Map.entry("NES", "Nintendo - Nintendo Entertainment System"),
            Map.entry("SNES", "Nintendo - Super Nintendo Entertainment System"),
            Map.entry("Nintendo 64", "Nintendo - Nintendo 64"),
            Map.entry("N64", "Nintendo - Nintendo 64"),
            Map.entry("Game Boy", "Nintendo - Game Boy"),
            Map.entry("Game Boy Color", "Nintendo - Game Boy Color"),
            Map.entry("GBA", "Nintendo - Game Boy Advance"),
            Map.entry("Game Boy Advance", "Nintendo - Game Boy Advance"),
            Map.entry("Nintendo DS", "Nintendo - Nintendo DS"),
            Map.entry("GameCube", "Nintendo - GameCube"),
            Map.entry("Wii", "Nintendo - Wii"),
            Map.entry("Master System", "Sega - Master System - Mark III"),
            Map.entry("Mega Drive", "Sega - Mega Drive - Genesis"),
            Map.entry("Genesis", "Sega - Mega Drive - Genesis"),
            Map.entry("Sega CD", "Sega - Mega-CD - Sega CD"),
            Map.entry("Sega 32X", "Sega - 32X"),
            Map.entry("Game Gear", "Sega - Game Gear"),
            Map.entry("Saturn", "Sega - Saturn"),
            Map.entry("Dreamcast", "Sega - Dreamcast"),
            Map.entry("PlayStation", "Sony - PlayStation"),
            Map.entry("PSX", "Sony - PlayStation"),
            Map.entry("PlayStation 2", "Sony - PlayStation 2"),
            Map.entry("PS2", "Sony - PlayStation 2"),
            Map.entry("PSP", "Sony - PlayStation Portable"),
            Map.entry("Atari 2600", "Atari - 2600"));

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
        String repo = LIBRETRO_REPOS_BY_PLATFORM.get(platform);
        if (repo == null) {
            return Optional.empty();
        }

        URI candidateUri = UriComponentsBuilder.fromUriString(libretroBaseUrl)
                .pathSegment(repo, media, escapeLibretroTitle(title) + ".png")
                .build()
                .encode()
                .toUri();

        return probeExists(candidateUri) ? Optional.of(candidateUri.toString()) : Optional.empty();
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

    private String escapeLibretroTitle(String title) {
        return title
                .replace("&", "_")
                .replace(":", "_")
                .replace("/", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_")
                .replace("\"", "_'");
    }
}
