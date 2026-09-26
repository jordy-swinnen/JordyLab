package dev.jordy.jordylab.gamecatalog.rest.client;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.headRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest(httpPort = 9996)
class ArtworkLookupClientTest {

    private static final String LOOKUP_BASE_URL = "http://localhost:9996";

    private ArtworkLookupClient artworkLookupClient;

    @BeforeEach
    void setUp() {
        artworkLookupClient = new ArtworkLookupClient(properties(), "https://cdn.example/steam/apps",
                LOOKUP_BASE_URL);
    }

    @Test
    void steamGamesResolveToDeterministicCdnUrlWithoutProbing() {
        Optional<String> url = artworkLookupClient.findExternalArtworkUrl(SourceType.STEAM, "Steam", "620",
                "Portal 2");

        assertThat(url).contains("https://cdn.example/steam/apps/620/header.jpg");
        verify(0, headRequestedFor(urlEqualTo("/anything")));
    }

    @Test
    void romGameResolvesWhenLibretroProbeHits() {
        stubFor(head(urlEqualTo("/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/Super%20Mario%20World.png"))
                .willReturn(aResponse().withStatus(200)));

        Optional<String> url = artworkLookupClient.findExternalArtworkUrl(SourceType.EMUDECK, "SNES", "smw.smc",
                "Super Mario World");

        assertThat(url).contains(LOOKUP_BASE_URL
                + "/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/Super%20Mario%20World.png");
    }

    @Test
    void romGameIsEmptyWhenLibretroProbeMisses() {
        stubFor(head(urlEqualTo("/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/Unknown%20Game.png"))
                .willReturn(aResponse().withStatus(404)));

        Optional<String> url = artworkLookupClient.findExternalArtworkUrl(SourceType.EMUDECK, "SNES", "x.smc",
                "Unknown Game");

        assertThat(url).isEmpty();
    }

    @Test
    void romGameIsEmptyWhenPlatformHasNoRepoMapping() {
        Optional<String> url = artworkLookupClient.findExternalArtworkUrl(SourceType.EMUDECK, "WonderSwan",
                "x.ws", "Some Game");

        assertThat(url).isEmpty();
        verify(0, headRequestedFor(urlEqualTo("/anything")));
    }

    @Test
    void libretroFilenameEscapingFollowsConvention() {
        stubFor(head(urlEqualTo("/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/A_B_C_D_E_F_G_H_I_'J.png"))
                .willReturn(aResponse().withStatus(200)));

        Optional<String> url = artworkLookupClient.findExternalArtworkUrl(SourceType.EMUDECK, "SNES", "ab.smc",
                "A&B:C/D*E?F<G>H|I\"J");

        assertThat(url).contains(LOOKUP_BASE_URL
                + "/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/A_B_C_D_E_F_G_H_I_'J.png");
    }

    @Test
    void probeFailureIsEmpty() {
        stubFor(head(urlEqualTo("/Sony%20-%20PlayStation/Named_Boxarts/Crash%20Bandicoot.png"))
                .willReturn(aResponse().withStatus(500)));

        Optional<String> url = artworkLookupClient.findExternalArtworkUrl(SourceType.EMUDECK, "PlayStation",
                "crash.chd", "Crash Bandicoot");

        assertThat(url).isEmpty();
    }

    private GameCatalogProperties properties() {
        return new GameCatalogProperties(
                                new GameCatalogProperties.Artwork("/tmp/artwork", 2097152L, true, 2000L),
                30,
                new GameCatalogProperties.Enrichment(50, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144));
    }
}
