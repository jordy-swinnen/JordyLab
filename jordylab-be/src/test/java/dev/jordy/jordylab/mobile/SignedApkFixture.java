package dev.jordy.jordylab.mobile;

import com.android.apksig.ApkSigner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Test helper that creates a minimal, signed APK (JAR) using the JDK's {@code keytool} and
 * {@code jarsigner} tools. The generated keystore and signed JAR live in a temporary directory.
 */
public final class SignedApkFixture {

    private static final String STORE_PASS = "changeit";
    private static final String KEY_PASS = "changeit";
    private static final String ALIAS = "test";

    private final Path tempDir;
    private final Path keystore;
    private final Path signedApk;
    private final String sha256Fingerprint;

    public SignedApkFixture(Path tempDir) {
        this.tempDir = tempDir;
        this.keystore = tempDir.resolve("test.keystore");
        this.signedApk = tempDir.resolve("test-signed.apk");
        try {
            createKeystore();
            createJar(signedApk);
            signJar();
            this.sha256Fingerprint = readFingerprint();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create signed APK fixture", exception);
        }
    }

    public Path signedApk() {
        return signedApk;
    }

    /**
     * The same content signed the way release builds are (AGP, minSdk 29): APK Signature Scheme v2 + v3, no v1
     * JAR signature, using Google's apksig ApkSigner with this fixture's key.
     */
    public Path v2v3SignedApk() {
        try {
            Path unsigned = tempDir.resolve("unsigned-for-v2.apk");
            Path signed = tempDir.resolve("test-v2v3-signed.apk");
            createJar(unsigned);
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(keystore)) {
                keyStore.load(in, STORE_PASS.toCharArray());
            }
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(ALIAS, KEY_PASS.toCharArray());
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(ALIAS);
            ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder(ALIAS, privateKey, List.of(certificate))
                    .build();
            new ApkSigner.Builder(List.of(signer))
                    .setInputApk(unsigned.toFile())
                    .setOutputApk(signed.toFile())
                    .setMinSdkVersion(29)
                    .setV1SigningEnabled(false)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(true)
                    .build()
                    .sign();
            return signed;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create v2/v3-signed APK fixture", exception);
        }
    }

    public String sha256Fingerprint() {
        return sha256Fingerprint;
    }

    private void createKeystore() throws IOException, InterruptedException {
        run(new ProcessBuilder(
                "keytool",
                "-genkeypair",
                "-alias", ALIAS,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "1",
                "-keystore", keystore.toString(),
                "-storepass", STORE_PASS,
                "-keypass", KEY_PASS,
                "-dname", "CN=Test"));
    }

    private void createJar(Path jar) throws IOException {
        try (OutputStream fileOut = Files.newOutputStream(jar);
                JarOutputStream jarOut = new JarOutputStream(fileOut)) {
            jarOut.putNextEntry(new JarEntry("AndroidManifest.xml"));
            jarOut.write(minimalBinaryAndroidManifest());
            jarOut.closeEntry();
            JarEntry entry = new JarEntry("classes.dex");
            jarOut.putNextEntry(entry);
            jarOut.write("dummy".getBytes(StandardCharsets.UTF_8));
            jarOut.closeEntry();
        }
    }

    private void signJar() throws IOException, InterruptedException {
        run(new ProcessBuilder(
                "jarsigner",
                "-keystore", keystore.toString(),
                "-storepass", STORE_PASS,
                "-keypass", KEY_PASS,
                signedApk.toString(),
                ALIAS));
    }

    private String readFingerprint() throws IOException, InterruptedException {
        String output = output(new ProcessBuilder(
                "keytool",
                "-list",
                "-v",
                "-keystore", keystore.toString(),
                "-storepass", STORE_PASS));
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("SHA256:")) {
                return trimmed.substring("SHA256:".length()).trim().replace(":", "").toLowerCase();
            }
        }
        throw new IllegalStateException("Could not read SHA-256 fingerprint from generated keystore");
    }

    /**
     * A {@code <manifest/>} element in Android's binary XML format (XML chunk → string pool with one UTF-16
     * string → start element → end element). apksig's {@code ApkVerifier} refuses an APK without an
     * {@code AndroidManifest.xml}; real APKs always carry one.
     */
    private static byte[] minimalBinaryAndroidManifest() {
        ByteBuffer buffer = ByteBuffer.allocate(120).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 0x0003).putShort((short) 8).putInt(120);            // RES_XML_TYPE
        buffer.putShort((short) 0x0001).putShort((short) 28).putInt(52);            // string pool
        buffer.putInt(1).putInt(0).putInt(0).putInt(32).putInt(0);                  // 1 string, UTF-16
        buffer.putInt(0);                                                           // offset of string 0
        buffer.putShort((short) 8);                                                 // "manifest", 8 chars
        for (char character : "manifest".toCharArray()) {
            buffer.putChar(character);
        }
        buffer.putShort((short) 0);
        buffer.putShort((short) 0x0102).putShort((short) 16).putInt(36);            // start element
        buffer.putInt(1).putInt(-1).putInt(-1).putInt(0);                           // line, comment, ns, name
        buffer.putShort((short) 20).putShort((short) 20).putShort((short) 0);       // no attributes
        buffer.putShort((short) 0).putShort((short) 0).putShort((short) 0);
        buffer.putShort((short) 0x0103).putShort((short) 16).putInt(24);            // end element
        buffer.putInt(1).putInt(-1).putInt(-1).putInt(0);

        return buffer.array();
    }

    private static void run(ProcessBuilder builder) throws IOException, InterruptedException {
        Process process = builder.inheritIO().start();
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("External command failed with exit code " + exit);
        }
    }

    private static String output(ProcessBuilder builder) throws IOException, InterruptedException {
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("External command failed with exit code " + exit + ": " + result);
        }
        return result;
    }
}
