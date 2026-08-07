package dev.jordy.jordylab.gamecatalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jordylab.gamecatalog")
public record GameCatalogProperties(
        Artwork artwork,
        int gracePeriodDays,
        Enrichment enrichment,
        Chat chat,
        Scan scan) {

    public GameCatalogProperties {
        artwork = artwork == null ? new Artwork(null, 0, null, 0) : artwork;
        if (gracePeriodDays <= 0) {
            gracePeriodDays = 30;
        }
        enrichment = enrichment == null ? new Enrichment(0, 0) : enrichment;
        chat = chat == null ? new Chat(0) : chat;
        scan = scan == null ? new Scan(0, 0, 0) : scan;
    }

    public record Artwork(String dir, long maxBytes, Boolean externalLookupEnabled, long lookupTimeoutMs) {
        public Artwork {
            dir = dir == null ? "/var/jordylab/artwork" : dir;
            if (maxBytes <= 0) {
                maxBytes = 2097152L;
            }
            externalLookupEnabled = externalLookupEnabled == null || externalLookupEnabled;
            if (lookupTimeoutMs <= 0) {
                lookupTimeoutMs = 2000L;
            }
        }
    }

    public record Enrichment(int batchSize, int maxAttempts) {
        public Enrichment {
            if (batchSize <= 0) {
                batchSize = 50;
            }
            if (maxAttempts <= 0) {
                maxAttempts = 3;
            }
        }
    }

    public record Chat(int maxResultGames) {
        public Chat {
            if (maxResultGames <= 0) {
                maxResultGames = 50;
            }
        }
    }

    public record Scan(int maxGamesPerSource, int maxPayloadBytes, int maxManifestBytesPerSource) {
        public Scan {
            if (maxGamesPerSource <= 0) {
                maxGamesPerSource = 10000;
            }
            if (maxPayloadBytes <= 0) {
                maxPayloadBytes = 1_048_576;
            }
            if (maxManifestBytesPerSource <= 0) {
                maxManifestBytesPerSource = 262_144;
            }
        }
    }
}
