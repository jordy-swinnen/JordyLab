package dev.jordy.jordylab.gamecatalog.util;

import lombok.experimental.UtilityClass;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalises a game title into the key that decides whether two catalog entries without a shared external id are
 * the same game (spec 013 FR-024, research B2): letter case, accents, punctuation, bracketed region/revision tags,
 * well-known edition suffixes and a leading "the" never make two titles different. Pure and static on purpose: the
 * Flyway Java migration that merges existing duplicates uses the very same code as the running application.
 */
@UtilityClass
public class TitleKeys {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern BRACKETED_TAG = Pattern.compile("[\\(\\[][^\\)\\]]*[\\)\\]]");
    private static final Pattern EDITION_SUFFIX = Pattern.compile(
            "\\b(game of the year|goty|definitive|deluxe|complete|enhanced|special|ultimate|gold|collector'?s|standard|legendary)"
                    + " edition\\b");
    private static final Pattern APOSTROPHES = Pattern.compile("['’`]");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern LEADING_THE = Pattern.compile("^the ");

    /** The key stored on a game: the normalised title, or the lower-cased title when normalising would leave nothing. */
    public static String keyFor(String title) {
        String key = normalise(title);

        return key.isEmpty() ? title.trim().toLowerCase(Locale.ROOT) : key;
    }

    public static String normalise(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String normalised = Normalizer.normalize(title, Normalizer.Form.NFKD);
        normalised = COMBINING_MARKS.matcher(normalised).replaceAll("");
        normalised = BRACKETED_TAG.matcher(normalised).replaceAll(" ");
        normalised = normalised.toLowerCase(Locale.ROOT).replace("&", " and ");
        normalised = EDITION_SUFFIX.matcher(normalised).replaceAll(" ");
        normalised = APOSTROPHES.matcher(normalised).replaceAll("");
        normalised = NON_ALPHANUMERIC.matcher(normalised).replaceAll(" ").trim();
        normalised = LEADING_THE.matcher(normalised).replaceFirst("");

        return normalised.trim();
    }
}
