package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogAccess;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class ManagedAppTest {

    @Test
    void keysAreUnique() {
        List<String> keys = Arrays.stream(ManagedApp.values()).map(ManagedApp::key).toList();

        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    void gameCatalogUsesTheSharedKey() {
        assertThat(ManagedApp.GAME_CATALOG.key()).isEqualTo(GameCatalogAccess.APP_KEY);
    }

    @Test
    void onlyTheGameCatalogIsGrantableAndADefaultGrant() {
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
    void fromKeyFindsAKnownAppAndRejectsAnUnknownOne() {
        assertSoftly(softly -> {
            softly.assertThat(ManagedApp.fromKey(GameCatalogAccess.APP_KEY)).contains(ManagedApp.GAME_CATALOG);
            softly.assertThat(ManagedApp.fromKey("unknown")).isEmpty();
            softly.assertThat(ManagedApp.fromKey(null)).isEmpty();
        });
    }
}
