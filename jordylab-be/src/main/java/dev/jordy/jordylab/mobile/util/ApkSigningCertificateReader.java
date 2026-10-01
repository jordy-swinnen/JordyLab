package dev.jordy.jordylab.mobile.util;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Reads the SHA-256 fingerprint of the certificate an APK is signed with (spec 007 FR-003, research D7).
 *
 * <p>Uses Google's apksig {@code ApkVerifier}, the reference implementation of every APK signature scheme
 * (v1 JAR, v2, v3). Release builds with {@code minSdk 29} carry only v2/v3 signatures, which a plain
 * {@link java.util.jar.JarFile} can't see (spec 011 BUG-037). Verification is checked from Android 10 (API 29,
 * spec 007 FR-018) upwards, so the APK's own manifest isn't needed to pick the schemes.
 */
@UtilityClass
public class ApkSigningCertificateReader {

    private static final int MIN_SUPPORTED_ANDROID_API = 29;
    private static final int HEX_RADIX = 16;

    public static String sha256Fingerprint(Path apkFile) {
        ApkVerifier.Result result;
        try {
            result = new ApkVerifier.Builder(apkFile.toFile())
                    .setMinCheckedPlatformVersion(MIN_SUPPORTED_ANDROID_API)
                    .build()
                    .verify();
        } catch (IOException | ApkFormatException | NoSuchAlgorithmException | IllegalStateException exception) {
            throw new InvalidApkException("Unable to read APK signing certificate", exception);
        }

        List<X509Certificate> signerCertificates = result.getSignerCertificates();
        if (!result.isVerified() || signerCertificates.isEmpty()) {
            throw new InvalidApkException("APK is not signed, or its signature does not verify");
        }
        // Release builds have exactly one signer; with several, "the" certificate to pin is ambiguous.
        if (signerCertificates.size() != 1) {
            throw new InvalidApkException("APK has " + signerCertificates.size() + " signers; expected exactly one");
        }

        try {
            return sha256Hex(signerCertificates.getFirst().getEncoded());
        } catch (GeneralSecurityException exception) {
            throw new InvalidApkException("Unable to read APK signing certificate", exception);
        }
    }

    private static String sha256Hex(byte[] encodedCertificate) throws GeneralSecurityException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(encodedCertificate);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            String byteHex = Integer.toString((b & 0xff) + 0x100, HEX_RADIX).substring(1);
            hex.append(byteHex);
        }

        return hex.toString();
    }
}
