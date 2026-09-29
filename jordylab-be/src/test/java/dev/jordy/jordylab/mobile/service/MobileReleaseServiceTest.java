package dev.jordy.jordylab.mobile.service;

import dev.jordy.jordylab.mobile.MobileProperties;
import dev.jordy.jordylab.mobile.SignedApkFixture;
import dev.jordy.jordylab.mobile.domain.MobileRelease;
import dev.jordy.jordylab.mobile.domain.repository.MobileReleaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MobileReleaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");

    private final MobileReleaseRepository repository = Mockito.mock(MobileReleaseRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void rejectsAVersionCodeEqualToTheCurrentLatest() {
        MobileReleaseService service = serviceWithFingerprint("a".repeat(64));
        MobileRelease latest = aMobileReleaseWithVersionCode(7);
        when(repository.findTopByOrderByVersionCodeDesc()).thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.publish("1.0.0", 7, "notes", dummyApk(), null))
                .isInstanceOf(VersionCodeNotMonotonicException.class);
    }

    @Test
    void rejectsAVersionCodeBelowTheCurrentLatest() {
        MobileReleaseService service = serviceWithFingerprint("a".repeat(64));
        MobileRelease latest = aMobileReleaseWithVersionCode(10);
        when(repository.findTopByOrderByVersionCodeDesc()).thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.publish("1.0.0", 5, "notes", dummyApk(), null))
                .isInstanceOf(VersionCodeNotMonotonicException.class);
    }

    @Test
    void rejectsAnApkWhoseSigningCertificateDoesNotMatch() {
        SignedApkFixture fixture = new SignedApkFixture(tempDir);
        MobileReleaseService service = serviceWithFingerprint("b".repeat(64));
        when(repository.findTopByOrderByVersionCodeDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish("1.0.0", 1, "notes", apkFrom(fixture.signedApk()), null))
                .isInstanceOf(SigningCertMismatchException.class);
    }

    @Test
    void publishesAReleaseWhenTheApkIsValid() throws Exception {
        SignedApkFixture fixture = new SignedApkFixture(tempDir);
        MobileReleaseService service = serviceWithFingerprint(fixture.sha256Fingerprint());
        when(repository.findTopByOrderByVersionCodeDesc()).thenReturn(Optional.empty());
        when(repository.save(any(MobileRelease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MobileRelease release = service.publish("1.0.0", 1, "notes", apkFrom(fixture.signedApk()), null);

        assertThat(release.getVersionName()).isEqualTo("1.0.0");
        assertThat(release.getVersionCode()).isEqualTo(1);
        assertThat(release.getMinSupportedVersionCode()).isEqualTo(1);
        assertThat(release.getStorageKey()).isEqualTo("jordylab-1.0.0.apk");
        assertThat(release.getPublishedAt()).isEqualTo(NOW);
        assertThat(tempDir.resolve(release.getStorageKey())).exists();
    }

    @Test
    void publishUsesMinSupportedVersionCodeOverride() throws Exception {
        SignedApkFixture fixture = new SignedApkFixture(tempDir);
        MobileReleaseService service = serviceWithFingerprint(fixture.sha256Fingerprint());
        MobileRelease latest = aMobileReleaseWithVersionCode(5);
        when(repository.findTopByOrderByVersionCodeDesc()).thenReturn(Optional.of(latest));
        when(repository.save(any(MobileRelease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MobileRelease release = service.publish("1.1.0", 10, "notes", apkFrom(fixture.signedApk()), 7);

        assertThat(release.getMinSupportedVersionCode()).isEqualTo(7);
    }

    @Test
    void publishFallsBackToLatestMinSupportedVersionCode() throws Exception {
        SignedApkFixture fixture = new SignedApkFixture(tempDir);
        MobileReleaseService service = serviceWithFingerprint(fixture.sha256Fingerprint());
        MobileRelease latest = aMobileReleaseWithVersionCode(5);
        when(repository.findTopByOrderByVersionCodeDesc()).thenReturn(Optional.of(latest));
        when(repository.save(any(MobileRelease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MobileRelease release = service.publish("1.1.0", 10, "notes", apkFrom(fixture.signedApk()), null);

        assertThat(release.getMinSupportedVersionCode()).isEqualTo(5);
    }

    @Test
    void resolveStoragePathReturnsReleaseFileUnderConfiguredDir() {
        MobileReleaseService service = serviceWithFingerprint("a".repeat(64));
        MobileRelease release = aMobileReleaseWithVersionCode(1);

        Path path = service.resolveStoragePath(release);

        assertThat(path).isEqualTo(tempDir.resolve(release.getStorageKey()));
    }

    @Test
    void latestReturnsEmptyWhenNoReleasesArePublished() {
        MobileReleaseService service = serviceWithFingerprint("a".repeat(64));
        when(repository.findTopByOrderByVersionCodeDesc()).thenReturn(Optional.empty());

        assertThat(service.latest()).isEmpty();
    }

    @Test
    void findByIdThrowsWhenNotFound() {
        MobileReleaseService service = serviceWithFingerprint("a".repeat(64));
        UUID unknownId = UUID.randomUUID();
        when(repository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(unknownId))
                .isInstanceOf(ReleaseNotFoundException.class);
    }

    private MobileReleaseService serviceWithFingerprint(String fingerprint) {
        MobileProperties properties = new MobileProperties(
                new MobileProperties.Release(tempDir.toString(), fingerprint),
                new MobileProperties.DownloadLink("secret", 5),
                new MobileProperties.App(null, null),
                null);
        return new MobileReleaseService(repository, properties, clock);
    }

    private MobileRelease aMobileReleaseWithVersionCode(int versionCode) {
        return MobileRelease.builder()
                .versionName("1.0.0")
                .versionCode(versionCode)
                .releaseNotes("notes")
                .sha256("a".repeat(64))
                .sizeBytes(1024)
                .minSupportedVersionCode(versionCode)
                .storageKey("jordylab-1.0.0.apk")
                .publishedAt(NOW)
                .build();
    }

    private MockMultipartFile dummyApk() {
        return new MockMultipartFile("file", "jordylab.apk", "application/vnd.android.package-archive",
                "not-a-real-apk".getBytes());
    }

    private MockMultipartFile apkFrom(Path file) throws Exception {
        return new MockMultipartFile("file", "jordylab.apk", "application/vnd.android.package-archive",
                Files.readAllBytes(file));
    }
}
