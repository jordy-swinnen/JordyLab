package dev.jordy.jordylab.gamecatalog.service.libbot;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The languages LibBot answers in (spec 013 FR-056). Anything else is {@code OTHER} and gets one fixed reply. */
public enum Language {
    @JsonProperty("en") EN("en"),
    @JsonProperty("nl") NL("nl"),
    @JsonProperty("other") OTHER("other");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
