package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameInstallationTest {

    @Test
    void buildGameInstallation() {
        GameInstallation installation = GameInstallationTestBuilder.aDefaultGameInstallation();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(installation.getId()).isNotNull();
            softly.assertThat(installation.getGame()).isEqualTo(GameTestBuilder.aDefaultGame());
            softly.assertThat(installation.getSource()).isEqualTo(ScanSourceTestBuilder.aDefaultScanSource());
            softly.assertThat(installation.getExternalRef())
                    .isEqualTo(GameInstallationTestBuilder.DEFAULT_EXTERNAL_REF);
            softly.assertThat(installation.getPlatform()).isEqualTo(GameInstallationTestBuilder.DEFAULT_PLATFORM);
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
    void buildWithoutPlatform() {
        assertThatThrownBy(() -> GameInstallationTestBuilder.aGameInstallation().platform(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEmulatedCopyStartsWithAnUnknownRomStatus() {
        ScanSource emulator = ScanSourceTestBuilder.aScanSource().sourceType(SourceType.EMUDECK).build();

        GameInstallation installation = GameInstallationTestBuilder.aGameInstallation().source(emulator).build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(installation.isEmulated()).isTrue();
            softly.assertThat(installation.getRomStatus()).isEqualTo(RomStatus.UNKNOWN);
        });
    }

    @Test
    void aSteamCopyHasNoRomStatus() {
        GameInstallation installation = GameInstallationTestBuilder.aDefaultGameInstallation();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(installation.isEmulated()).isFalse();
            softly.assertThat(installation.getRomStatus()).isNull();
        });
    }

    @Test
    void buildingASteamCopyWithARomStatusFails() {
        assertThatThrownBy(() -> GameInstallationTestBuilder.aGameInstallation().romStatus(RomStatus.BROKEN).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only applies to emulated copies");
    }

    @Test
    void changeRomStatusOnAnEmulatedCopy() {
        ScanSource emulator = ScanSourceTestBuilder.aScanSource().sourceType(SourceType.EMUDECK).build();
        GameInstallation installation = GameInstallationTestBuilder.aGameInstallation().source(emulator).build();

        installation.changeRomStatus(RomStatus.VALIDATED);
        assertThat(installation.getRomStatus()).isEqualTo(RomStatus.VALIDATED);

        installation.changeRomStatus(RomStatus.BROKEN);
        assertThat(installation.getRomStatus()).isEqualTo(RomStatus.BROKEN);

        installation.changeRomStatus(RomStatus.UNKNOWN);
        assertThat(installation.getRomStatus()).isEqualTo(RomStatus.UNKNOWN);
    }

    @Test
    void changeRomStatusOnASteamCopyIsRejected() {
        GameInstallation installation = GameInstallationTestBuilder.aDefaultGameInstallation();

        assertThatThrownBy(() -> installation.changeRomStatus(RomStatus.BROKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only applies to emulated copies");
    }

    @Test
    void changeRomStatusRequiresAStatus() {
        ScanSource emulator = ScanSourceTestBuilder.aScanSource().sourceType(SourceType.EMUDECK).build();
        GameInstallation installation = GameInstallationTestBuilder.aGameInstallation().source(emulator).build();

        assertThatThrownBy(() -> installation.changeRomStatus(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRomStatusSurvivesBeingUninstalledAndSeenAgain() {
        ScanSource emulator = ScanSourceTestBuilder.aScanSource().sourceType(SourceType.EMUDECK).build();
        GameInstallation installation = GameInstallationTestBuilder.aGameInstallation().source(emulator).build();
        installation.changeRomStatus(RomStatus.VALIDATED);

        installation.markUninstalled(GameInstallationTestBuilder.DEFAULT_LAST_SEEN);
        installation.seenAgain(GameInstallationTestBuilder.DEFAULT_LAST_SEEN.plusSeconds(60));

        assertThat(installation.getRomStatus()).isEqualTo(RomStatus.VALIDATED);
    }

    @Test
    void updatePlatformChangesThePlatformOfThisCopyOnly() {
        GameInstallation installation = GameInstallationTestBuilder.aDefaultGameInstallation();

        installation.updatePlatform("Super Nintendo");

        assertThat(installation.getPlatform()).isEqualTo("Super Nintendo");
        assertThatThrownBy(() -> installation.updatePlatform(" ")).isInstanceOf(IllegalArgumentException.class);
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
