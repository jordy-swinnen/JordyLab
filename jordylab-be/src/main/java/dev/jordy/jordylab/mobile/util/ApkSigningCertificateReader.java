package dev.jordy.jordylab.mobile.util;

import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Reads the SHA-256 fingerprint of the certificate an APK is signed with (spec FR-003, research
 * D7). An APK is a signed JAR (Signature Scheme v1) — opening it with verification enabled and
 * fully reading one non-{@code META-INF/} entry populates {@link JarEntry#getCertificates()}
 * with the signer chain; the first certificate is the signing certificate.
 *
 * <p><strong>Implementation note:</strong> this has not been exercised against a real signed APK
 * in this environment (no Android SDK / build tooling available) — verify against a real release
 * build before relying on it in production.
 */
@UtilityClass
public class ApkSigningCertificateReader {

    private static final int HEX_RADIX = 16;

    public static String sha256Fingerprint(Path apkFile) {
        try (JarFile jarFile = new JarFile(apkFile.toFile(), true)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                fullyRead(jarFile, entry);
                Certificate[] certificates = entry.getCertificates();
                if (certificates != null && certificates.length > 0) {
                    return sha256Hex(certificates[0].getEncoded());
                }
            }
            throw new IllegalArgumentException("APK is not signed: no signing certificate found");
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalArgumentException("Unable to read APK signing certificate", exception);
        }
    }

    private static void fullyRead(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream in = jarFile.getInputStream(entry)) {
            in.readAllBytes();
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
