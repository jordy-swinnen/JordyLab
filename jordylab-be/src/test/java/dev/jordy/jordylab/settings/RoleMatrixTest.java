package dev.jordy.jordylab.settings;

import dev.jordy.jordylab.shared.ai.ResilientAiService;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Proves the deny-by-default access matrix against real Keycloak-issued tokens:
 * admin reaches everything, guest reaches Game Catalog reads and chat only, a pending
 * user is refused everywhere, and the scanner role stays confined to ingest scan/check.
 */
class RoleMatrixTest extends KeycloakIntegrationTest {

    // Unstubbed AI calls return a mock AiCallResult whose success() is false, so chat
    // degrades to its explicit failure instead of reaching a real provider.
    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    private ResilientAiService resilientAiService;

    @Test
    void adminReachesFnaSettingsAndGameCatalogAdministration() {
        String admin = accessTokenFor("admin-user", "admin-password");

        assertSoftly(softly -> {
            softly.assertThat(status(as(get("/api/fna/articles"), admin))).isEqualTo(200);
            // The settings API arrives with US2; an authorized admin falls through to 404, never 403.
            softly.assertThat(status(as(get("/api/settings/users"), admin))).isEqualTo(404);
            softly.assertThat(status(as(get("/api/gamecatalog/games"), admin))).isEqualTo(200);
            softly.assertThat(status(as(post("/api/gamecatalog/games/refresh"), admin))).isEqualTo(200);
            softly.assertThat(status(as(get("/api/gamecatalog/library/status"), admin))).isEqualTo(200);
            softly.assertThat(status(as(get("/api/gamecatalog/ingest/client?libraryType=steam"), admin))).isEqualTo(200);
            // No release has been published in this test fixture — an authorized admin falls
            // through to 404 (NO_RELEASES_PUBLISHED), never 403 (spec 007 FR-002).
            softly.assertThat(status(as(get("/api/mobile/releases/latest"), admin))).isEqualTo(404);
            // Admin does not hold mobile-release-publisher — publishing is CI-only (FR-004).
            softly.assertThat(status(as(post("/api/mobile/releases"), admin))).isEqualTo(403);
            // "Save to FNA" (spec 007 FR-017) reuses the existing /api/fna/** admin-only matcher.
            softly.assertThat(status(as(manualArticleRequest(), admin))).isEqualTo(201);
        });
    }

    @Test
    void guestReachesGameCatalogReadsAndChatOnly() {
        String guest = accessTokenFor("guest-user", "guest-password");

        assertSoftly(softly -> {
            softly.assertThat(status(as(get("/api/gamecatalog/games"), guest))).isEqualTo(200);
            softly.assertThat(status(as(get("/api/gamecatalog/platforms"), guest))).isEqualTo(200);
            softly.assertThat(status(as(get("/api/gamecatalog/hosts"), guest))).isEqualTo(200);
            // Chat is authorized for guests; only a 403 would mean the matrix refused them.
            softly.assertThat(status(as(chatRequest(), guest))).isNotEqualTo(403);
            softly.assertThat(status(as(get("/api/fna/articles"), guest))).isEqualTo(403);
            softly.assertThat(status(as(get("/api/settings/users"), guest))).isEqualTo(403);
            softly.assertThat(status(as(post("/api/gamecatalog/games/refresh"), guest))).isEqualTo(403);
            softly.assertThat(status(as(get("/api/gamecatalog/library/status"), guest))).isEqualTo(403);
            softly.assertThat(status(as(get("/api/gamecatalog/ingest/client?libraryType=steam"), guest))).isEqualTo(403);
            // Guests may check for updates and request a download link (spec 007 FR-002).
            softly.assertThat(status(as(get("/api/mobile/releases/latest"), guest))).isEqualTo(404);
            softly.assertThat(status(as(post("/api/mobile/releases"), guest))).isEqualTo(403);
            // "Save to FNA" is admin-only (spec 007 FR-017) — a guest only ever sees "Ask the catalog".
            softly.assertThat(status(as(manualArticleRequest(), guest))).isEqualTo(403);
        });
    }

    @Test
    void pendingUserIsDeniedEverywhere() {
        String pending = accessTokenFor("pending-user", "pending-password");

        assertSoftly(softly -> {
            softly.assertThat(status(as(get("/api/fna/articles"), pending))).isEqualTo(403);
            softly.assertThat(status(as(get("/api/settings/users"), pending))).isEqualTo(403);
            softly.assertThat(status(as(get("/api/gamecatalog/games"), pending))).isEqualTo(403);
            softly.assertThat(status(as(chatRequest(), pending))).isEqualTo(403);
            softly.assertThat(status(as(get("/api/gamecatalog/ingest/client?libraryType=steam"), pending))).isEqualTo(403);
            // spec 007 scenario 4: a pending user gets no dialog and a denied download-link request.
            softly.assertThat(status(as(get("/api/mobile/releases/latest"), pending))).isEqualTo(403);
            softly.assertThat(status(as(post("/api/mobile/releases/" + UUID.randomUUID() + "/download-link"), pending)))
                    .isEqualTo(403);
        });
    }

    @Test
    void downloadAndAssetLinksEndpointsArePublic() {
        assertSoftly(softly -> {
            // No release published in this fixture, so an unauthenticated caller reaches the
            // controller (proving permitAll) and gets refused by token validation, never 401/403
            // from Spring Security itself (spec 007 research D8).
            softly.assertThat(status(get("/api/mobile/download/not-a-real-token"))).isEqualTo(403);
            softly.assertThat(status(get("/.well-known/assetlinks.json"))).isEqualTo(200);
        });
    }

    @Test
    void scannerRoleIsConfinedToIngestCheckAndScan() {
        String scanner = accessTokenFor("scanner-user", "scanner-password");
        String admin = accessTokenFor("admin-user", "admin-password");

        assertSoftly(softly -> {
            softly.assertThat(status(as(get("/api/fna/articles"), scanner))).isEqualTo(403);
            softly.assertThat(status(as(get("/api/gamecatalog/games"), scanner))).isEqualTo(403);
            softly.assertThat(status(as(get("/api/gamecatalog/ingest/client?libraryType=steam"), scanner))).isEqualTo(403);
            // The scanner passes authorization on scan/check (request validation decides the rest);
            // an admin does not hold the scanner role and is refused.
            softly.assertThat(status(as(ingestCheckRequest(), scanner))).isNotEqualTo(403);
            softly.assertThat(status(as(ingestCheckRequest(), admin))).isEqualTo(403);
            softly.assertThat(status(as(ingestScanRequest(), scanner))).isNotEqualTo(403);
        });
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        assertSoftly(softly -> {
            softly.assertThat(status(get("/api/fna/articles"))).isEqualTo(401);
            softly.assertThat(status(get("/api/gamecatalog/games"))).isEqualTo(401);
        });
    }

    private MockHttpServletRequestBuilder chatRequest() {
        return post("/api/gamecatalog/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"anything\"}");
    }

    private MockHttpServletRequestBuilder ingestCheckRequest() {
        return post("/api/gamecatalog/ingest/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
    }

    private MockHttpServletRequestBuilder manualArticleRequest() {
        return post("/api/fna/articles/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.invalid/article-" + UUID.randomUUID() + "\"}");
    }

    private MockHttpServletRequestBuilder ingestScanRequest() {
        return post("/api/gamecatalog/ingest/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private int status(MockHttpServletRequestBuilder request) {
        try {
            return mockMvc.perform(request).andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new IllegalStateException("MockMvc request failed", exception);
        }
    }
}
