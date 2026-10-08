package dev.jordy.jordylab.gamecatalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code jordylab.gamecatalog.libbot.*}: conversation memory and answer limits (spec 013 FR-003, FR-011, FR-012).
 */
@ConfigurationProperties(prefix = "jordylab.gamecatalog.libbot")
public record LibBotProperties(int memoryExchanges, int memoryIdleHours, int maxReferences, int maxCandidates,
        int maxUnknownTitles, int maxMessageLength) {

    public LibBotProperties {
        if (memoryExchanges <= 0) {
            memoryExchanges = 10;
        }
        if (memoryIdleHours <= 0) {
            memoryIdleHours = 2;
        }
        if (maxReferences <= 0) {
            maxReferences = 10;
        }
        if (maxCandidates <= 0) {
            maxCandidates = 15;
        }
        if (maxUnknownTitles <= 0) {
            maxUnknownTitles = 5;
        }
        if (maxMessageLength <= 0) {
            maxMessageLength = 1000;
        }
    }
}
