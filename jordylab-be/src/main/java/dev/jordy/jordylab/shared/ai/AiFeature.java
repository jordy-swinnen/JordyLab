package dev.jordy.jordylab.shared.ai;

import java.util.Arrays;
import java.util.Optional;

/**
 * Every place JordyLab calls an AI model (006 FR-011, research D4). Each feature gets its own model: from the AI Models
 * settings page when one is saved, otherwise from {@code jordylab.ai.features.<key>.model}.
 */
public enum AiFeature {

    FNA_BRIEFING("fna.briefing", "Monthly briefing", "fna",
            "Writes the monthly investment briefing from the latest news and the portfolio."),
    GAMECATALOG_ENRICHMENT("gamecatalog.enrichment", "Game descriptions", "gamecatalog",
            "Writes each game's description and fills in genre and play modes."),
    GAMECATALOG_CHAT_QUERY("gamecatalog.chat.query", "Chat: understanding the question", "gamecatalog",
            "Turns a chat question into a search over the catalog."),
    GAMECATALOG_CHAT_ANSWER("gamecatalog.chat.answer", "Chat: writing the answer", "gamecatalog",
            "Writes the chat answer from the games the search found.");

    private final String key;
    private final String displayName;
    private final String module;
    private final String description;

    AiFeature(String key, String displayName, String module, String description) {
        this.key = key;
        this.displayName = displayName;
        this.module = module;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String module() {
        return module;
    }

    public String description() {
        return description;
    }

    public static Optional<AiFeature> fromKey(String key) {
        return Arrays.stream(values()).filter(feature -> feature.key.equals(key)).findFirst();
    }
}
