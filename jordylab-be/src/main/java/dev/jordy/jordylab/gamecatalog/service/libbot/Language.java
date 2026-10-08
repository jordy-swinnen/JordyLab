package dev.jordy.jordylab.gamecatalog.service.libbot;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * The languages LibBot answers in (spec 013 FR-056). Anything else is {@code OTHER} and gets one fixed reply. The wire
 * values are the constant names (the JSON schema advertises them); the lowercase codes are accepted too, because the
 * recorded goldens use them (BUG-076).
 */
public enum Language {
    EN("en"),
    NL("nl"),
    OTHER("other");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    @JsonCreator
    public static Language from(String value) {
        for (Language language : values()) {
            if (language.name().equalsIgnoreCase(value) || language.code.equalsIgnoreCase(value)) {
                return language;
            }
        }

        return OTHER;
    }
}
