package dev.jordy.jordylab.gamecatalog.service.scan;

import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal Valve KeyValues (VDF) parser. Steam's {@code appmanifest_*.acf}
 * files are a tiny subset of the VDF grammar: a top-level "AppState" object
 * with primitive string and numeric leaves. We only need a small subset
 * (appid, name, installdir, StateFlags, etc.) so the parser is hand-rolled
 * rather than pulling in a dependency.
 *
 * <p>The grammar accepted here is: balanced braces, with each line either a
 * key on its own followed by a value, or a key + braced object. Quoted and
 * unquoted keys are both accepted. Quoted string values and bareword numeric
 * values are both accepted. Comment lines (starting with {@code //}) are
 * skipped.
 */
@UtilityClass
class VdfParser {

    /**
     * Parse a VDF document into a nested map. The top-level keys appear as
     * direct entries; values that are themselves VDF objects become nested
     * maps; everything else becomes the raw string of the value.
     */
    Map<String, Object> parse(String text) {
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        Map<String, Object> root = new LinkedHashMap<>();
        ParserState state = new ParserState(text);
        state.skipWhitespaceAndComments();
        while (state.hasMore()) {
            String key = state.readKey();
            if (key == null) {
                break;
            }
            state.skipWhitespaceAndComments();
            Object value = state.readValue();
            root.put(key, value);
            state.skipWhitespaceAndComments();
        }

        return root;
    }

    /**
     * Convenience: look up a key in a nested map. Returns {@code defaultValue}
     * if any key in the path is absent.
     */
    String nestedString(Map<String, Object> root, String defaultValue, String... keys) {
        Object current = root;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return defaultValue;
            }
            current = map.get(key);
        }

        return current == null ? defaultValue : current.toString();
    }

    private static final class ParserState {

        private final String text;
        private int index;

        ParserState(String text) {
            this.text = text;
        }

        boolean hasMore() {
            return index < text.length();
        }

        void skipWhitespaceAndComments() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (Character.isWhitespace(c)) {
                    index++;
                } else if (c == '/' && index + 1 < text.length() && text.charAt(index + 1) == '/') {
                    while (index < text.length() && text.charAt(index) != '\n') {
                        index++;
                    }
                } else {
                    break;
                }
            }
        }

        String readKey() {
            if (index >= text.length()) {
                return null;
            }
            char first = text.charAt(index);
            if (first == '"') {
                return readQuoted();
            }
            if (first == '{' || first == '}') {
                return null;
            }
            int start = index;
            while (index < text.length()) {
                char c = text.charAt(index);
                if (Character.isWhitespace(c) || c == '{' || c == '"') {
                    break;
                }
                index++;
            }

            return text.substring(start, index);
        }

        private String readQuoted() {
            index++;
            StringBuilder sb = new StringBuilder();
            while (index < text.length() && text.charAt(index) != '"') {
                sb.append(text.charAt(index));
                index++;
            }
            if (index < text.length()) {
                index++;
            }

            return sb.toString();
        }

        Object readValue() {
            if (index >= text.length()) {
                return "";
            }
            char c = text.charAt(index);
            if (c == '"') {
                return readQuoted();
            }
            if (c == '{') {
                index++;
                return readObject();
            }

            int start = index;
            while (index < text.length() && !Character.isWhitespace(text.charAt(index))) {
                index++;
            }

            return text.substring(start, index);
        }

        private Map<String, Object> readObject() {
            Map<String, Object> object = new LinkedHashMap<>();
            while (index < text.length()) {
                skipWhitespaceAndComments();
                if (index >= text.length()) {
                    break;
                }
                if (text.charAt(index) == '}') {
                    index++;

                    return object;
                }
                String key = readKey();
                if (key == null) {
                    break;
                }
                skipWhitespaceAndComments();
                object.put(key, readValue());
            }

            return object;
        }
    }
}
