package dev.jordy.jordylab.gamecatalog.domain;

/** Chip colours as CSS hex values; {@code border} is null for chips without an outline. */
public record ChipColors(String background, String foreground, String border) {

    public static ChipColors of(String background, String foreground) {
        return new ChipColors(background, foreground, null);
    }
}
