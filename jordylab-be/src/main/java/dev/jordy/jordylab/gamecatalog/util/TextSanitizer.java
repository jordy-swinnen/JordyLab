package dev.jordy.jordylab.gamecatalog.util;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class TextSanitizer {

    private static final String SCRIPT_STYLE_PATTERN = "(?is)<(script|style)[^>]*>.*?</\\1>";
    private static final String MARKUP_PATTERN = "<[^>]*>";
    private static final String CONTROL_PATTERN = "[\\p{Cntrl}&&[^\\t]]";
    private static final String WHITESPACE_PATTERN = "\\s+";

    // A scene release suffix: a run of scene tags ending in "<platform>-<group>" at the very end, e.g.
    // "BurnOut USA MULTi5 PROPER READNFO PSP-MUPSP". A title such as "Born in the USA" has no group suffix and stays.
    private static final String SCENE_TAG = "(?:USA|EUR|JPN|JAP|WORLD|NTSC|PAL|MULTi\\d?|PROPER|READNFO|REPACK"
            + "|RERIP|FIX|PSN|PSP|PS2|PS3|PSX|PSV|NDS|N3DS|GBA|GBC|GB|SNES|NES|N64|GC|WIIU|WII|SWITCH"
            + "|XBOX|X360|XBX|PC)";
    private static final Pattern SCENE_SUFFIX_PATTERN = Pattern.compile(
            "\\s+" + SCENE_TAG + "(?:\\s+" + SCENE_TAG + ")*\\s*-\\s*[A-Za-z0-9]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEC_SUFFIX_PATTERN = Pattern.compile("\\s+\\.?dec$", Pattern.CASE_INSENSITIVE);

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

    /** Removes a scene release suffix and a decrypted-image suffix from an EmuDeck title. */
    public static String stripSceneReleaseTags(String rawTitle) {
        if (rawTitle == null) {
            return null;
        }
        String withoutSceneSuffix = SCENE_SUFFIX_PATTERN.matcher(rawTitle).replaceAll("");
        String withoutDecSuffix = DEC_SUFFIX_PATTERN.matcher(withoutSceneSuffix).replaceAll("");

        return withoutDecSuffix.replaceAll(WHITESPACE_PATTERN, " ").trim();
    }
}
