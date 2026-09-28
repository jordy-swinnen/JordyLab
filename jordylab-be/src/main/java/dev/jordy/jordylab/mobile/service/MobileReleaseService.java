package dev.jordy.jordylab.mobile.service;

import dev.jordy.jordylab.mobile.MobileProperties;
import dev.jordy.jordylab.mobile.domain.MobileRelease;
import dev.jordy.jordylab.mobile.domain.repository.MobileReleaseRepository;
import dev.jordy.jordylab.mobile.util.ApkSigningCertificateReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Publishing, lookup, and file resolution for signed Android releases (spec FR-001, FR-003,
 * FR-011; research D7). Every publish is validated (signing-cert match, monotonic versionCode)
 * before the file is kept or the row created — a rejected publish leaves nothing behind.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MobileReleaseService {

    private final MobileReleaseRepository repository;
    private final MobileProperties properties;
    private final Clock clock;

    public Optional<MobileRelease> latest() {
        return repository.findTopByOrderByVersionCodeDesc();
    }

    public MobileRelease findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ReleaseNotFoundException(id));
    }

    public MobileRelease publish(String versionName, int versionCode, String releaseNotes,
            MultipartFile apkFile, Integer minSupportedVersionCodeOverride) {
        Optional<MobileRelease> currentLatest = latest();
        int currentLatestVersionCode = currentLatest.map(MobileRelease::getVersionCode).orElse(0);
        if (versionCode <= currentLatestVersionCode) {
            throw new VersionCodeNotMonotonicException(versionCode, currentLatestVersionCode);
        }
        int minSupportedVersionCode = minSupportedVersionCodeOverride != null
                ? minSupportedVersionCodeOverride
                : currentLatest.map(MobileRelease::getMinSupportedVersionCode).orElse(versionCode);

        Path storageDir = ensureStorageDir();
        Path tempFile = writeToTempFile(apkFile, storageDir);
        try {
            verifySigningCertificate(tempFile);
            String sha256 = sha256Hex(tempFile);
            long sizeBytes = Files.size(tempFile);
            String storageKey = "jordylab-" + versionName + ".apk";
            Files.move(tempFile, storageDir.resolve(storageKey), StandardCopyOption.REPLACE_EXISTING);

            MobileRelease release = MobileRelease.builder()
                    .versionName(versionName)
                    .versionCode(versionCode)
                    .releaseNotes(releaseNotes)
                    .sha256(sha256)
                    .sizeBytes(sizeBytes)
                    .minSupportedVersionCode(minSupportedVersionCode)
                    .storageKey(storageKey)
                    .publishedAt(clock.instant())
                    .build();

            return repository.save(release);
        } catch (IOException exception) {
            deleteQuietly(tempFile);
            throw new IllegalStateException("Unable to finalize APK release", exception);
        } catch (RuntimeException failure) {
            deleteQuietly(tempFile);
            throw failure;
        }
    }

    public Path resolveStoragePath(MobileRelease release) {
        return Path.of(properties.release().storageDir()).resolve(release.getStorageKey());
    }

    private void verifySigningCertificate(Path apkFile) {
        String actual = ApkSigningCertificateReader.sha256Fingerprint(apkFile);
        if (!properties.release().signingCertSha256().equalsIgnoreCase(actual)) {
            throw new SigningCertMismatchException();
        }
    }

    private Path ensureStorageDir() {
        try {
            Path dir = Path.of(properties.release().storageDir());
            Files.createDirectories(dir);

            return dir;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create mobile release storage directory", exception);
        }
    }

    private Path writeToTempFile(MultipartFile apkFile, Path storageDir) {
        try {
            Path tempFile = Files.createTempFile(storageDir, "upload-", ".apk");
            try (InputStream in = apkFile.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return tempFile;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store uploaded APK", exception);
        }
    }

    private String sha256Hex(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file);
                    DigestInputStream digestIn = new DigestInputStream(in, digest)) {
                digestIn.readAllBytes();
            }
            StringBuilder hex = new StringBuilder(digest.getDigestLength() * 2);
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to compute APK SHA-256", exception);
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("Failed to delete rejected upload {}", file, exception);
        }
    }
}
