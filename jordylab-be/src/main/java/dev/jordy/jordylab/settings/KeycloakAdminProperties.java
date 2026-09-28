package dev.jordy.jordylab.settings;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jordylab.settings.keycloak")
public record KeycloakAdminProperties(
        String serverUrl,
        String realm,
        String adminClientId,
        String adminClientSecret,
        int timeoutMs) {

    public KeycloakAdminProperties {
        serverUrl = serverUrl == null || serverUrl.isBlank() ? "http://localhost:8180" : serverUrl;
        realm = realm == null || realm.isBlank() ? "jordylab" : realm;
        adminClientId = adminClientId == null || adminClientId.isBlank() ? "jordylab-backend" : adminClientId;
        timeoutMs = timeoutMs <= 0 ? 5000 : timeoutMs;
    }
}
