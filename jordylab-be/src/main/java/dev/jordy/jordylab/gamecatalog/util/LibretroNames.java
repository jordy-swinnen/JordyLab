package dev.jordy.jordylab.gamecatalog.util;

import lombok.experimental.UtilityClass;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The file names a title may have in the libretro thumbnail repositories (spec 013 FR-019/FR-020). Libretro names follow
 * No-Intro: a region tag, a comma-moved leading article, a dash for a colon. A scanned title rarely matches on the first
 * try, so the likely forms are tried in order and the first that exists wins. Capped, because every form costs a request.
 */
@UtilityClass
public class LibretroNames {

    private static final int MAX_VARIANTS = 16;
    private static final List<String> REGION_TAGS = List.of("(USA)", "(Europe)", "(World)", "(USA, Europe)", "(Japan)");

    public static List<String> variants(String title) {
        if (title == null || title.isBlank()) {
            return List.of();
        }
        String trimmed = title.trim();
        Set<String> forms = new LinkedHashSet<>();
        boolean tagged = trimmed.endsWith(")");
        List<String> bases = baseForms(trimmed);
        bases.forEach(base -> forms.add(escape(base)));
        if (!tagged) {
            // Tag outer, base inner: the likeliest regions are tried for every spelling before the unlikely ones.
            for (String tag : REGION_TAGS) {
                bases.forEach(base -> forms.add(escape(base) + " " + tag));
            }
        }

        return forms.stream().limit(MAX_VARIANTS).toList();
    }

    /** The title itself, with a colon turned into a dash, and with a leading article moved behind a comma. */
    private static List<String> baseForms(String title) {
        Set<String> bases = new LinkedHashSet<>();
        bases.add(title);
        String dashed = title.replace(": ", " - ").replace(":", " -");
        bases.add(dashed);
        for (String article : List.of("The ", "A ", "An ")) {
            if (title.startsWith(article)) {
                String rest = title.substring(article.length());
                bases.add(moveArticle(rest, article.trim()));
                bases.add(moveArticle(rest.replace(": ", " - "), article.trim()));
            }
        }

        return List.copyOf(bases);
    }

    /** "Legend of Zelda - A Link" with "The" becomes "Legend of Zelda, The - A Link": the comma follows the first part. */
    private static String moveArticle(String rest, String article) {
        int dash = rest.indexOf(" - ");
        if (dash < 0) {
            return rest + ", " + article;
        }

        return rest.substring(0, dash) + ", " + article + rest.substring(dash);
    }

    /** Characters the libretro naming replaces with an underscore. */
    static String escape(String name) {
        return name
                .replace("&", "_")
                .replace(":", "_")
                .replace("/", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_")
                .replace("\"", "_'");
    }
}
