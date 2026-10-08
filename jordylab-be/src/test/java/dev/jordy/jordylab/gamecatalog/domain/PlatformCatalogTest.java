package dev.jordy.jordylab.gamecatalog.domain;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCatalogTest {

    private static final double MINIMUM_TEXT_CONTRAST = 4.5d;

    @ParameterizedTest(name = "\"{0}\" is \"{1}\"")
    @CsvSource({
            "N64,Nintendo 64",
            "nintendo 64,Nintendo 64",
            "GBA,Game Boy Advance",
            "PSX,PlayStation",
            "PS1,PlayStation",
            "ps2,PlayStation 2",
            "Nintendo GameCube,GameCube",
            "Sega Saturn,Saturn",
            "Mega Drive,Genesis",
            "Super Nintendo,SNES",
            "SNES MSU-1,SNES",
            "PlayStation Portable,PSP",
            "Xbox Series X,Xbox Series X|S",
            "Switch,Nintendo Switch",
            "Switch 2,Nintendo Switch 2",
            "Nintendo Switch,Nintendo Switch"
    })
    void everyAliasResolvesToOneCanonicalName(String spelling, String canonical) {
        assertThat(PlatformCatalog.canonical(spelling)).isEqualTo(canonical);
    }

    @Test
    void anUnknownPlatformKeepsItsTrimmedNameAndGetsTheNeutralEntry() {
        PlatformEntry custom = PlatformCatalog.entryFor("  My Arcade Cabinet ");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(PlatformCatalog.canonical("  My Arcade Cabinet ")).isEqualTo("My Arcade Cabinet");
            softly.assertThat(custom.name()).isEqualTo("My Arcade Cabinet");
            softly.assertThat(custom.family()).isEqualTo(BrandFamily.OTHER);
            softly.assertThat(custom.igdbPlatformId()).isNull();
            softly.assertThat(custom.isKnownConsole()).isFalse();
        });
    }

    @Test
    void blankAndNullNamesAreNotInTheCatalog() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(PlatformCatalog.find(null)).isEmpty();
            softly.assertThat(PlatformCatalog.find("  ")).isEmpty();
            softly.assertThat(PlatformCatalog.canonical(null)).isNull();
        });
    }

    @Test
    void knownConsolesStartAtGenerationFiveAndReachTheCurrentOne() {
        List<PlatformEntry> consoles = PlatformCatalog.knownConsoles();
        List<String> names = consoles.stream().map(PlatformEntry::name).toList();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(consoles).allSatisfy(console -> softly.assertThat(console.generation()).isGreaterThanOrEqualTo(5));
            softly.assertThat(names).contains("PlayStation", "PlayStation 2", "PlayStation 3", "PlayStation 4",
                    "PlayStation 5", "Xbox", "Xbox 360", "Xbox One", "Xbox Series X|S", "Nintendo 64", "GameCube", "Wii",
                    "Wii U", "Nintendo Switch", "Nintendo Switch 2", "Saturn", "Dreamcast", "Game Boy Color",
                    "Game Boy Advance", "Nintendo DS", "Nintendo 3DS", "PSP", "PlayStation Vita");
            softly.assertThat(names).doesNotContain("SNES", "NES", "Genesis", "Steam", "Atari 2600");
            softly.assertThat(consoles.get(0).generation()).isEqualTo(5);
            softly.assertThat(consoles.get(consoles.size() - 1).generation()).isEqualTo(9);
        });
    }

    @Test
    void theVerifiedIgdbIdsAreStored() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(PlatformCatalog.entryFor("PlayStation").igdbPlatformId()).isEqualTo(7L);
            softly.assertThat(PlatformCatalog.entryFor("Nintendo Switch").igdbPlatformId()).isEqualTo(130L);
            softly.assertThat(PlatformCatalog.entryFor("Nintendo Switch 2").igdbPlatformId()).isEqualTo(508L);
            softly.assertThat(PlatformCatalog.entryFor("PlayStation 5").igdbPlatformId()).isEqualTo(167L);
            softly.assertThat(PlatformCatalog.entryFor("Xbox Series X|S").igdbPlatformId()).isEqualTo(169L);
            softly.assertThat(PlatformCatalog.entryFor("Nintendo 64").igdbPlatformId()).isEqualTo(4L);
            softly.assertThat(PlatformCatalog.entryFor("Steam").igdbPlatformId()).isNull();
        });
    }

    @Test
    void brandFamiliesUseTheOfficialColours() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(PlatformCatalog.entryFor("PlayStation 5").colors().background()).isEqualTo("#0070D1");
            softly.assertThat(PlatformCatalog.entryFor("Xbox 360").colors().background()).isEqualTo("#107C10");
            softly.assertThat(PlatformCatalog.entryFor("Nintendo Switch").colors().background()).isEqualTo("#E60012");
            softly.assertThat(PlatformCatalog.entryFor("Dreamcast").colors().background()).isEqualTo("#0089CF");
            softly.assertThat(PlatformCatalog.entryFor("Steam").colors().foreground()).isEqualTo("#66C0F4");
            softly.assertThat(PlatformCatalog.entryFor("Some Custom Thing").colors().background()).isEqualTo("#3A3552");
        });
    }

    @Test
    void everyChipTextMeetsTheWcagContrastMinimum() {
        SoftAssertions.assertSoftly(softly -> {
            for (BrandFamily family : BrandFamily.values()) {
                ChipColors colors = family.chipColors();
                softly.assertThat(contrastRatio(colors.foreground(), colors.background()))
                        .as("%s text %s on %s", family, colors.foreground(), colors.background())
                        .isGreaterThanOrEqualTo(MINIMUM_TEXT_CONTRAST);
            }
        });
    }

    @Test
    void noSpellingBelongsToTwoPlatforms() {
        assertThat(PlatformCatalog.all()).extracting(PlatformEntry::name).doesNotHaveDuplicates();
    }

    private static double contrastRatio(String firstHex, String secondHex) {
        double first = relativeLuminance(firstHex);
        double second = relativeLuminance(secondHex);

        return (Math.max(first, second) + 0.05d) / (Math.min(first, second) + 0.05d);
    }

    private static double relativeLuminance(String hex) {
        int red = Integer.parseInt(hex.substring(1, 3), 16);
        int green = Integer.parseInt(hex.substring(3, 5), 16);
        int blue = Integer.parseInt(hex.substring(5, 7), 16);

        return 0.2126d * linear(red) + 0.7152d * linear(green) + 0.0722d * linear(blue);
    }

    private static double linear(int channel) {
        double scaled = channel / 255d;

        return scaled <= 0.03928d ? scaled / 12.92d : Math.pow((scaled + 0.055d) / 1.055d, 2.4d);
    }
}
