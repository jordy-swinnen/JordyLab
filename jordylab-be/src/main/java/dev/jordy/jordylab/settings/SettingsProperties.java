package dev.jordy.jordylab.settings;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jordylab.settings")
public record SettingsProperties(
        GuestChat guestChat,
        ModelCatalog modelCatalog,
        Notifications notifications) {

    public SettingsProperties {
        guestChat = guestChat == null ? new GuestChat(0, null) : guestChat;
        modelCatalog = modelCatalog == null ? new ModelCatalog(0) : modelCatalog;
        notifications = notifications == null ? new Notifications(null) : notifications;
    }

    /**
     * Per-guest daily chat budget and the time zone whose midnight resets it.
     */
    public record GuestChat(int dailyLimit, String zone) {
        public GuestChat {
            if (dailyLimit <= 0) {
                dailyLimit = 20;
            }
            zone = zone == null || zone.isBlank() ? "Europe/Brussels" : zone;
        }
    }

    /**
     * How long the trimmed gateway model catalog is served from cache.
     */
    public record ModelCatalog(int cacheTtlMinutes) {
        public ModelCatalog {
            if (cacheTtlMinutes <= 0) {
                cacheTtlMinutes = 60;
            }
        }
    }

    public record Notifications(Ntfy ntfy) {
        public Notifications {
            ntfy = ntfy == null ? new Ntfy(null, null, null) : ntfy;
        }
    }

    /**
     * Optional sign-up push; the notifier stays a no-op while baseUrl or topic is blank.
     */
    public record Ntfy(String baseUrl, String topic, String token) {
    }
}
