package dev.jordy.jordylab.mobile.service;

import dev.jordy.jordylab.mobile.MobileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DownloadLinkServiceTest {

    private static final UUID RELEASE_ID = UUID.fromString("7c8d9e0f-1a2b-4c3d-8e4f-5a6b7c8d9e0f");
    private static final Instant ISSUED_AT = Instant.parse("2026-09-28T10:00:00Z");

    private final MobileProperties properties = new MobileProperties(
            new MobileProperties.Release(null, null),
            new MobileProperties.DownloadLink("test-secret", 5),
            new MobileProperties.App(null, null),
            null);

    private Clock clock;
    private DownloadLinkService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(ISSUED_AT, ZoneOffset.UTC);
        service = new DownloadLinkService(properties, clock);
    }

    @Test
    void issuedTokenVerifiesToTheSameReleaseId() {
        DownloadLinkService.IssuedDownloadLink issued = service.issue(RELEASE_ID, "guest-subject");

        assertThat(service.verify(issued.token())).isEqualTo(RELEASE_ID);
        assertThat(issued.expiresAt()).isEqualTo(ISSUED_AT.plusSeconds(5 * 60));
    }

    @Test
    void tamperedSignatureIsRejected() {
        DownloadLinkService.IssuedDownloadLink issued = service.issue(RELEASE_ID, "guest-subject");
        String tampered = issued.token().substring(0, issued.token().length() - 1) + "x";

        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(DownloadLinkInvalidException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        DownloadLinkService.IssuedDownloadLink issued = service.issue(RELEASE_ID, "guest-subject");
        Clock afterExpiry = Clock.fixed(ISSUED_AT.plusSeconds(6 * 60), ZoneOffset.UTC);
        DownloadLinkService serviceAfterExpiry = new DownloadLinkService(properties, afterExpiry);

        assertThatThrownBy(() -> serviceAfterExpiry.verify(issued.token()))
                .isInstanceOf(DownloadLinkInvalidException.class);
    }

    @Test
    void malformedTokenIsRejected() {
        assertThatThrownBy(() -> service.verify("not-a-valid-token"))
                .isInstanceOf(DownloadLinkInvalidException.class);
    }

    @Test
    void nullTokenIsRejected() {
        assertThatThrownBy(() -> service.verify(null))
                .isInstanceOf(DownloadLinkInvalidException.class);
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        DownloadLinkService.IssuedDownloadLink issued = service.issue(RELEASE_ID, "guest-subject");
        MobileProperties otherSecretProperties = new MobileProperties(
                new MobileProperties.Release(null, null),
                new MobileProperties.DownloadLink("a-different-secret", 5),
                new MobileProperties.App(null, null),
                null);
        DownloadLinkService serviceWithDifferentSecret = new DownloadLinkService(otherSecretProperties, clock);

        assertThatThrownBy(() -> serviceWithDifferentSecret.verify(issued.token()))
                .isInstanceOf(DownloadLinkInvalidException.class);
    }
}
