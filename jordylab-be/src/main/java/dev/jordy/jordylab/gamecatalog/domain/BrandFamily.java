package dev.jordy.jordylab.gamecatalog.domain;

/**
 * The brand a platform belongs to and its chip colours (spec 013 FR-040, ui-design §3.1). The official brand colours
 * are checked for WCAG contrast by {@code PlatformCatalogTest}: the lighter official PlayStation blue is used because
 * the darker one is almost invisible on the page background, and Sega's official blue takes dark text because white
 * text on it misses 4.5:1.
 */
public enum BrandFamily {

    PLAYSTATION(ChipColors.of("#0070D1", "#FFFFFF")),
    XBOX(ChipColors.of("#107C10", "#FFFFFF")),
    NINTENDO(ChipColors.of("#E60012", "#FFFFFF")),
    SEGA(ChipColors.of("#0089CF", "#0B1220")),
    STEAM(new ChipColors("#1B2838", "#66C0F4", "#66C0F4")),
    OTHER(ChipColors.of("#3A3552", "#F2EDE4"));

    private final ChipColors chipColors;

    BrandFamily(ChipColors chipColors) {
        this.chipColors = chipColors;
    }

    public ChipColors chipColors() {
        return chipColors;
    }
}
