package dev.jordy.jordylab.mobile.util;

import dev.jordy.jordylab.mobile.SignedApkFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApkSigningCertificateReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsSha256FingerprintFromSignedApk() {
        SignedApkFixture fixture = new SignedApkFixture(tempDir);

        assertThat(ApkSigningCertificateReader.sha256Fingerprint(fixture.signedApk()))
                .isEqualTo(fixture.sha256Fingerprint());
    }

    @Test
    void rejectsUnsignedApk() {
        Path unsignedApk = tempDir.resolve("unsigned.apk");

        assertThatThrownBy(() -> ApkSigningCertificateReader.sha256Fingerprint(unsignedApk))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to read APK signing certificate");
    }
}
