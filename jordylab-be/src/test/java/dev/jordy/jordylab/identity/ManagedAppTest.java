package dev.jordy.jordylab.identity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * The app registry moved here from {@code settings} with spec 019. The Game Catalog key is spelled as the catalog's
 * own constant {@code GameCatalogAccess.APP_KEY}; identity deliberately does not import the gamecatalog module, so the
 * literal is asserted here and the catalog-side consistency is covered where the two are wired together.
 */
class ManagedAppTest {

    private static final String GAME_CATALOG_KEY = "gamecatalog";

    @Test
    void exposesEveryAppKey() {
        List<String> keys = java.util.Arrays.stream(ManagedApp.values()).map(ManagedApp::key).toList();

        assertThat(keys).containsExactly(GAME_CATALOG_KEY, "fna", "settings");
    }

    @Test
    void gameCatalogIsTheOnlyGrantableDefault() {
        assertSoftly(softly -> {
            softly.assertThat(ManagedApp.defaultGrants()).containsExactly(ManagedApp.GAME_CATALOG);
            softly.assertThat(ManagedApp.GAME_CATALOG.grantable()).isTrue();
            softly.assertThat(ManagedApp.GAME_CATALOG.grantRole()).contains("app-gamecatalog");
            softly.assertThat(ManagedApp.GAME_CATALOG.notGrantableReason()).isNull();
            softly.assertThat(ManagedApp.FNA.grantable()).isFalse();
            softly.assertThat(ManagedApp.FNA.grantRole()).isEmpty();
            softly.assertThat(ManagedApp.FNA.notGrantableReason()).isEqualTo("Holds the admin's private data");
            softly.assertThat(ManagedApp.SETTINGS.grantable()).isFalse();
            softly.assertThat(ManagedApp.SETTINGS.notGrantableReason()).isEqualTo("Administration only");
        });
    }

    @Test
    void resolvesAppsByKey() {
        assertSoftly(softly -> {
            softly.assertThat(ManagedApp.fromKey(GAME_CATALOG_KEY)).contains(ManagedApp.GAME_CATALOG);
            softly.assertThat(ManagedApp.fromKey("unknown")).isEmpty();
            softly.assertThat(ManagedApp.fromKey(null)).isEmpty();
        });
    }
}
