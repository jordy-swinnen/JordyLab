package dev.jordy.jordylab.gamecatalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = GameCatalogPropertiesTest.TestConfig.class,
        initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = {
        "jordylab.gamecatalog.scan.max-games-per-source=5000",
        "jordylab.gamecatalog.scan.max-payload-bytes=1048576",
        "jordylab.gamecatalog.scan.max-manifest-bytes-per-source=131072",
        "jordylab.gamecatalog.artwork.dir=/tmp/test-artwork",
        "jordylab.gamecatalog.artwork.max-bytes=1048576",
        "jordylab.gamecatalog.artwork.external-lookup-enabled=false",
        "jordylab.gamecatalog.artwork.lookup-timeout-ms=1500",
        "jordylab.gamecatalog.grace-period-days=14",
        "jordylab.gamecatalog.enrichment.batch-size=25",
        "jordylab.gamecatalog.enrichment.max-attempts=5",
        "jordylab.gamecatalog.chat.max-result-games=20"
})
class GameCatalogPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(GameCatalogProperties.class)
    static class TestConfig {
    }

    @Autowired
    private GameCatalogProperties properties;

    @Test
    void bindsScanProperties() {
        assertThat(properties.scan().maxGamesPerSource()).isEqualTo(5000);
        assertThat(properties.scan().maxPayloadBytes()).isEqualTo(1048576);
        assertThat(properties.scan().maxManifestBytesPerSource()).isEqualTo(131072);
    }

    @Test
    void bindsArtworkProperties() {
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(properties.artwork().dir()).isEqualTo("/tmp/test-artwork");
            softly.assertThat(properties.artwork().maxBytes()).isEqualTo(1048576L);
            softly.assertThat(properties.artwork().externalLookupEnabled()).isFalse();
            softly.assertThat(properties.artwork().lookupTimeoutMs()).isEqualTo(1500L);
        });
    }

    @Test
    void bindsLifecycleAndChatProperties() {
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(properties.gracePeriodDays()).isEqualTo(14);
            softly.assertThat(properties.enrichment().batchSize()).isEqualTo(25);
            softly.assertThat(properties.enrichment().maxAttempts()).isEqualTo(5);
            softly.assertThat(properties.chat().maxResultGames()).isEqualTo(20);
        });
    }

    @Test
    void appliesDocumentedDefaultsWhenSectionsAreAbsent() {
        GameCatalogProperties defaults = new GameCatalogProperties(null, 0, null, null, null);

        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(defaults.artwork().dir()).isEqualTo("/var/jordylab/artwork");
            softly.assertThat(defaults.artwork().maxBytes()).isEqualTo(2097152L);
            softly.assertThat(defaults.artwork().externalLookupEnabled()).isTrue();
            softly.assertThat(defaults.artwork().lookupTimeoutMs()).isEqualTo(2000L);
            softly.assertThat(defaults.gracePeriodDays()).isEqualTo(30);
            softly.assertThat(defaults.enrichment().batchSize()).isEqualTo(50);
            softly.assertThat(defaults.enrichment().maxAttempts()).isEqualTo(3);
            softly.assertThat(defaults.chat().maxResultGames()).isEqualTo(50);
            softly.assertThat(defaults.scan().maxGamesPerSource()).isEqualTo(10000);
            softly.assertThat(defaults.scan().maxPayloadBytes()).isEqualTo(1_048_576);
            softly.assertThat(defaults.scan().maxManifestBytesPerSource()).isEqualTo(262_144);
        });
    }
}
