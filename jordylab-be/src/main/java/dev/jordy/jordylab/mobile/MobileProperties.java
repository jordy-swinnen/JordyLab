package dev.jordy.jordylab.mobile;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jordylab.mobile")
public record MobileProperties(
        Release release,
        DownloadLink downloadLink,
        App app,
        Notifications notifications) {

    public MobileProperties {
        release = release == null ? new Release(null, null) : release;
        downloadLink = downloadLink == null ? new DownloadLink(null, 0) : downloadLink;
        app = app == null ? new App(null, null) : app;
        notifications = notifications == null ? new Notifications(null) : notifications;
    }

    /**
     * Where signed APK files live (a filesystem volume, mirroring gamecatalog's artwork-dir
     * pattern — research D7) and the release signing certificate's SHA-256 fingerprint, checked
     * against every published APK (FR-003). {@code signingCertSha256} is a placeholder until the
     * release keystore is generated (research D14, stop-and-report gate).
     */
    public record Release(String storageDir, String signingCertSha256) {
        public Release {
            storageDir = storageDir == null || storageDir.isBlank() ? "/var/jordylab/mobile-releases" : storageDir;
            signingCertSha256 = signingCertSha256 == null ? "" : signingCertSha256;
        }
    }

    /** Signed download-link token settings (research D8). */
    public record DownloadLink(String secret, int ttlMinutes) {
        public DownloadLink {
            ttlMinutes = ttlMinutes <= 0 ? 5 : ttlMinutes;
        }
    }

    /**
     * The Android application id and the public domain the app is served from — both
     * placeholders until feature 008 (production HTTPS deployment) and the application-id
     * decision (research D14, stop-and-report gate) are finalized. {@code productionDomain}
     * feeds App-Link click-through URLs (research D9) and the Keycloak redirect URI.
     */
    public record App(String packageName, String productionDomain) {
        public App {
            packageName = packageName == null ? "" : packageName;
            productionDomain = productionDomain == null ? "" : productionDomain;
        }
    }

    public record Notifications(Ntfy ntfy) {
        public Notifications {
            ntfy = ntfy == null ? new Ntfy(null, null, null) : ntfy;
        }
    }

    /**
     * Relocated from {@code jordylab.settings.notifications.ntfy.*} (research D10) — no code read
     * those keys before this feature, so this is a same-day rename, not a behavior change. The
     * notifier stays a no-op while baseUrl or topic is blank.
     */
    public record Ntfy(String baseUrl, String topic, String token) {
    }
}
