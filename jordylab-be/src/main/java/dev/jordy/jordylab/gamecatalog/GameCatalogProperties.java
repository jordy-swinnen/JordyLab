package dev.jordy.jordylab.gamecatalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jordylab.gamecatalog")
public record GameCatalogProperties(
        Artwork artwork,
        int gracePeriodDays,
        Enrichment enrichment,
        Chat chat,
        Metadata metadata,
        Scan scan,
        Library library) {

    public GameCatalogProperties {
        artwork = artwork == null ? new Artwork(null, 0, null, 0) : artwork;
        if (gracePeriodDays <= 0) {
            gracePeriodDays = 30;
        }
        enrichment = enrichment == null ? new Enrichment(0, 0) : enrichment;
        chat = chat == null ? new Chat(0) : chat;
        metadata = metadata == null ? new Metadata(0, 0) : metadata;
        scan = scan == null ? new Scan(0, 0, 0, 0) : scan;
        library = library == null ? new Library(0, 0, 0, 0) : library;
    }

    /** Steam library sync tuning: trigger interval, staleness hint, store pacing, HTTP timeout. */
    public record Library(int minIntervalMinutes, int staleAfterDays, int storeMinIntervalMs, int callTimeoutMs) {
        public Library {
            if (minIntervalMinutes <= 0) {
                minIntervalMinutes = 720;
            }
            if (staleAfterDays <= 0) {
                staleAfterDays = 14;
            }
            if (storeMinIntervalMs <= 0) {
                storeMinIntervalMs = 1500;
            }
            if (callTimeoutMs <= 0) {
                callTimeoutMs = 10000;
            }
        }
    }

    public record Metadata(int batchSize, int maxAttempts) {
        public Metadata {
            if (batchSize <= 0) {
                batchSize = 25;
            }
            if (maxAttempts <= 0) {
                maxAttempts = 3;
            }
        }
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
                batchSize = 8;
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

    public record Scan(int maxGamesPerSource, int maxPayloadBytes, int maxManifestBytesPerSource,
            double maxShrinkFraction) {
        public Scan {
            if (maxGamesPerSource <= 0) {
                maxGamesPerSource = 50000;
            }
            if (maxPayloadBytes <= 0) {
                maxPayloadBytes = 8_388_608;
            }
            if (maxManifestBytesPerSource <= 0) {
                maxManifestBytesPerSource = 262_144;
            }
            if (maxShrinkFraction <= 0d || maxShrinkFraction >= 1d) {
                maxShrinkFraction = 0.5d;
            }
        }
    }
}
