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
    GAMECATALOG_CHAT_QUERY("gamecatalog.chat.query", "LibBot: understanding the question", "gamecatalog",
            "Turns a LibBot question into the facts and filters used to search the library."),
    GAMECATALOG_CHAT_ANSWER("gamecatalog.chat.answer", "LibBot: writing the answer", "gamecatalog",
            "Writes LibBot's answer from the games the search found."),
    /** Configuration, not a choice: changing the model means re-embedding every game (spec 013 research A3). */
    GAMECATALOG_EMBEDDING("gamecatalog.embedding", "Game search index", "gamecatalog",
            "Turns each game into a vector so LibBot can find games by meaning.", false);

    private final String key;
    private final String displayName;
    private final String module;
    private final String description;
    private final boolean selectable;

    AiFeature(String key, String displayName, String module, String description) {
        this(key, displayName, module, description, true);
    }

    AiFeature(String key, String displayName, String module, String description, boolean selectable) {
        this.key = key;
        this.displayName = displayName;
        this.module = module;
        this.description = description;
        this.selectable = selectable;
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

    /** Whether an admin may pick this feature's model on Settings → AI Models. */
    public boolean selectable() {
        return selectable;
    }

    public static Optional<AiFeature> fromKey(String key) {
        return Arrays.stream(values()).filter(feature -> feature.key.equals(key)).findFirst();
    }
}
