package dev.jordy.jordylab.gamecatalog.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TextSanitizer {

    private static final String SCRIPT_STYLE_PATTERN = "(?is)<(script|style)[^>]*>.*?</\\1>";
    private static final String MARKUP_PATTERN = "<[^>]*>";
    private static final String CONTROL_PATTERN = "[\\p{Cntrl}&&[^\\t]]";
    private static final String WHITESPACE_PATTERN = "\\s+";

    public static String sanitizeTitle(String rawTitle) {
        if (rawTitle == null) {
            return null;
        }

        return rawTitle
                .replaceAll(SCRIPT_STYLE_PATTERN, "")
                .replaceAll(MARKUP_PATTERN, "")
                .replaceAll(CONTROL_PATTERN, "")
                .replaceAll(WHITESPACE_PATTERN, " ")
                .trim();
    }
}
