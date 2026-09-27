package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameInstallationTest {

    @Test
    void buildGameInstallation() {
        GameInstallation installation = GameInstallationTestBuilder.aDefaultGameInstallation();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(installation.getId()).isNotNull();
            softly.assertThat(installation.getExternalRef())
                    .isEqualTo(GameInstallationTestBuilder.DEFAULT_EXTERNAL_REF);
            softly.assertThat(installation.getPresence()).isEqualTo(Presence.INSTALLED);
            softly.assertThat(installation.getFirstSeenAt())
                    .isEqualTo(GameInstallationTestBuilder.DEFAULT_FIRST_SEEN);
            softly.assertThat(installation.getLastSeenAt())
                    .isEqualTo(GameInstallationTestBuilder.DEFAULT_LAST_SEEN);
            softly.assertThat(installation.isInstalled()).isTrue();
        });
    }

    @Test
    void buildWithoutGame() {
        assertThatThrownBy(() -> GameInstallationTestBuilder.aGameInstallation().game(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutSource() {
        assertThatThrownBy(() -> GameInstallationTestBuilder.aGameInstallation().source(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutExternalRef() {
        assertThatThrownBy(() -> GameInstallationTestBuilder.aGameInstallation().externalRef(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankExternalRef() {
        assertThatThrownBy(() -> GameInstallationTestBuilder.aGameInstallation().externalRef(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithTooLongExternalRef() {
        String tooLong = "x".repeat(501);

        assertThatThrownBy(() -> GameInstallationTestBuilder.aGameInstallation().externalRef(tooLong).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutFirstSeenAt() {
        assertThatThrownBy(() -> GameInstallationTestBuilder.aGameInstallation().firstSeenAt(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutLastSeenAt() {
        assertThatThrownBy(() -> GameInstallationTestBuilder.aGameInstallation().lastSeenAt(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markUninstalledThenSeenAgainRestoresInstalled() {
        GameInstallation installation = GameInstallationTestBuilder.aDefaultGameInstallation();

        installation.markUninstalled(GameInstallationTestBuilder.DEFAULT_LAST_SEEN);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(installation.getPresence()).isEqualTo(Presence.UNINSTALLED);
            softly.assertThat(installation.getUninstalledAt())
                    .isEqualTo(GameInstallationTestBuilder.DEFAULT_LAST_SEEN);
            softly.assertThat(installation.isInstalled()).isFalse();
        });

        installation.seenAgain(GameInstallationTestBuilder.DEFAULT_LAST_SEEN.plusSeconds(3600));
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(installation.getPresence()).isEqualTo(Presence.INSTALLED);
            softly.assertThat(installation.getUninstalledAt()).isNull();
            softly.assertThat(installation.getLastSeenAt())
                    .isEqualTo(GameInstallationTestBuilder.DEFAULT_LAST_SEEN.plusSeconds(3600));
        });
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(GameInstallation.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
