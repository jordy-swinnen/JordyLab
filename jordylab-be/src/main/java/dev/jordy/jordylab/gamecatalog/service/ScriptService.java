package dev.jordy.jordylab.gamecatalog.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Generates the per-library shell script that the user downloads from the
 * web UI. The script handles Keycloak Device Authorization Grant, reads
 * the host's {@code hostname}, walks the selected library, and POSTs the
 * listing to {@code /api/gamecatalog/ingest/scan}.
 *
 * <p>The script template lives at
 * {@code src/main/resources/scripts/jordylab-scan-template.sh} and is
 * committed to the repo so it can be reviewed and diffed. The generator
 * does simple placeholder substitution ({@code ${KEYCLOAK_URL}},
 * {@code ${REALM}}, {@code ${CLIENT_ID}}, {@code ${BACKEND_URL}},
 * {@code ${LIBRARY_TYPE}}).
 */
@Slf4j
@Service
public class ScriptService {

    private static final String TEMPLATE_PATH = "scripts/jordylab-scan-template.sh";

    private final String keycloakUrl;
    private final String realm;
    private final String clientId;
    private final String scriptTemplate;

    public ScriptService(@Value("${jordylab.script.keycloak-url:http://localhost:8180}") String keycloakUrl,
            @Value("${jordylab.script.realm:jordylab}") String realm,
            @Value("${jordylab.script.client-id:gamecatalog-script}") String clientId) {
        this.keycloakUrl = stripTrailingSlash(keycloakUrl);
        this.realm = realm;
        this.clientId = clientId;
        this.scriptTemplate = loadTemplate();
    }

    public String generateScript(String libraryType, HttpServletRequest httpRequest) {
        String normalized = libraryType == null ? "" : libraryType.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("steam") && !normalized.equals("emudeck")) {
            throw new IllegalArgumentException("libraryType must be 'steam' or 'emudeck'");
        }
        String backendUrl = resolveBackendUrl(httpRequest);

        Map<String, String> vars = new HashMap<>();
        vars.put("KEYCLOAK_URL", keycloakUrl);
        vars.put("REALM", realm);
        vars.put("CLIENT_ID", clientId);
        vars.put("BACKEND_URL", backendUrl);
        vars.put("LIBRARY_TYPE", normalized.toUpperCase(Locale.ROOT));
        vars.put("SCAN_ENDPOINT", "/api/gamecatalog/ingest/scan");

        return render(scriptTemplate, vars);
    }

    private static String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String resolveBackendUrl(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwardedScheme = request.getHeader("X-Forwarded-Proto");
        String scheme = StringUtils.hasText(forwardedScheme) ? forwardedScheme : request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean isDefaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);

        StringBuilder url = new StringBuilder().append(scheme).append("://").append(host);
        if (!isDefaultPort) {
            url.append(':').append(port);
        }

        return url.toString();
    }

    private static String render(String template, Map<String, String> vars) {
        StringBuilder out = new StringBuilder(template.length());
        int cursor = 0;
        while (cursor < template.length()) {
            int match = template.indexOf("${", cursor);
            if (match < 0) {
                out.append(template, cursor, template.length());

                break;
            }
            out.append(template, cursor, match);
            int close = template.indexOf('}', match + 2);
            if (close < 0) {
                out.append(template, cursor, template.length());

                break;
            }
            String key = template.substring(match + 2, close);
            String value = vars.getOrDefault(key, "");
            out.append(value);
            cursor = close + 1;
        }

        return out.toString();
    }

    private static String loadTemplate() {
        try (var input = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Script template not found: " + TEMPLATE_PATH, exception);
        }
    }
}
