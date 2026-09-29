package dev.jordy.jordylab.mobile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
