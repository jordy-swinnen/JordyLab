package dev.jordy.jordylab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Runs on a real servlet container, because the two failures it guards against don't exist in
 * MockMvc: Spring's default multipart limit (1 MB) rejecting any real APK, and that rejection — or
 * any other exception — being forwarded to {@code /error}, which the deny-by-default security
 * config answered with a bare 403 (spec 011, release v0.0.1-rc1).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:0/realms/test",
        "jordylab.mobile.release.storage-dir=${java.io.tmpdir}/jordylab-mobile-upload-test"
})
class MobileReleaseUploadIntegrationTest {

    private static final int TWO_MEGABYTES = 2 * 1024 * 1024;

    @Value("${local.server.port}")
    int port;

    @TestConfiguration
    static class PublisherJwtDecoder {

        @Bean
        @Primary
        JwtDecoder publisherJwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "service-account-mobile-release-ci")
                    .claim("realm_access", Map.of("roles", List.of("mobile-release-publisher")))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        }
    }

    @Test
    void aMultiMegabyteUploadThatIsNotAnApkIsRejectedAsInvalidNotForbidden() {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("versionName", "0.0.1-rc1");
        form.add("versionCode", "1");
        form.add("releaseNotes", "test");
        form.add("file", new ByteArrayResource(new byte[TWO_MEGABYTES]) {
            @Override
            public String getFilename() {
                return "app-release.apk";
            }
        });

        ResponseEntity<String> response = RestClient.create().post()
                .uri("http://localhost:" + port + "/api/mobile/releases")
                .header(HttpHeaders.AUTHORIZATION, "Bearer publisher-token")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .onStatus(status -> true, (request, clientResponse) -> { })
                .toEntity(String.class);

        assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode().value()).isEqualTo(400);
            softly.assertThat(response.getBody()).contains("INVALID_APK");
        });
    }
}
