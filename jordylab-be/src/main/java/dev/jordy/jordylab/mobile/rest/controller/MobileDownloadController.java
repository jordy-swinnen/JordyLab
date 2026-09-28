package dev.jordy.jordylab.mobile.rest.controller;

import dev.jordy.jordylab.mobile.domain.MobileRelease;
import dev.jordy.jordylab.mobile.service.DownloadLinkInvalidException;
import dev.jordy.jordylab.mobile.service.DownloadLinkService;
import dev.jordy.jordylab.mobile.service.MobileReleaseService;
import dev.jordy.jordylab.mobile.service.ReleaseNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Streams the signed APK for a valid download token (contracts/mobile-releases-api.md,
 * spec FR-002). Deliberately {@code permitAll} in {@code SecurityConfig} — Android's download
 * manager and a plain browser download can't carry a bearer token — the signed, time-boxed
 * token (research D8) is the only guard.
 */
@RestController
@RequiredArgsConstructor
public class MobileDownloadController {

    private static final MediaType ANDROID_PACKAGE_ARCHIVE = MediaType.parseMediaType(
            "application/vnd.android.package-archive");

    private final DownloadLinkService downloadLinkService;
    private final MobileReleaseService releaseService;

    @GetMapping("/api/mobile/download/{token}")
    public ResponseEntity<Resource> download(@PathVariable String token) {
        UUID releaseId = downloadLinkService.verify(token);
        MobileRelease release = releaseService.findById(releaseId);
        Path filePath = releaseService.resolveStoragePath(release);

        return ResponseEntity.ok()
                .contentType(ANDROID_PACKAGE_ARCHIVE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"jordylab-" + release.getVersionName() + ".apk\"")
                .body(new FileSystemResource(filePath));
    }

    @ExceptionHandler(DownloadLinkInvalidException.class)
    public ResponseEntity<ErrorBody> handleDownloadLinkInvalid(DownloadLinkInvalidException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorBody("DOWNLOAD_LINK_INVALID"));
    }

    /** A token can outlive the release it names only in pathological admin-deletion scenarios
     * that don't exist yet — treated the same as an invalid token rather than a 500. */
    @ExceptionHandler(ReleaseNotFoundException.class)
    public ResponseEntity<ErrorBody> handleReleaseNotFound(ReleaseNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorBody("DOWNLOAD_LINK_INVALID"));
    }

    private record ErrorBody(String reason) {
    }
}
