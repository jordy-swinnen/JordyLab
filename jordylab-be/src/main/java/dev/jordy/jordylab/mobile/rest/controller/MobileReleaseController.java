package dev.jordy.jordylab.mobile.rest.controller;

import dev.jordy.jordylab.mobile.domain.MobileRelease;
import dev.jordy.jordylab.mobile.rest.controller.model.DownloadLinkResponse;
import dev.jordy.jordylab.mobile.rest.controller.model.LatestReleaseResponse;
import dev.jordy.jordylab.mobile.rest.controller.model.PublishReleaseResponse;
import dev.jordy.jordylab.mobile.service.DownloadLinkService;
import dev.jordy.jordylab.mobile.service.MobileReleaseService;
import dev.jordy.jordylab.mobile.service.NoReleasesPublishedException;
import dev.jordy.jordylab.mobile.service.ReleaseNotFoundException;
import dev.jordy.jordylab.mobile.service.SigningCertMismatchException;
import dev.jordy.jordylab.mobile.service.VersionCodeNotMonotonicException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Release publishing, the update-check "latest" lookup, and download-link issuance
 * (contracts/mobile-releases-api.md, spec FR-001–FR-004, FR-011). Role gating for every route
 * here lives in {@code SecurityConfig} — {@code latest}/{@code download-link} require
 * {@code admin} or {@code guest}, {@code POST /releases} requires the
 * {@code mobile-release-publisher} service account.
 */
@RestController
@RequestMapping("/api/mobile/releases")
@RequiredArgsConstructor
public class MobileReleaseController {

    private final MobileReleaseService releaseService;
    private final DownloadLinkService downloadLinkService;

    @GetMapping("/latest")
    public LatestReleaseResponse latest(@RequestParam(required = false) Integer installedVersionCode) {
        MobileRelease release = releaseService.latest().orElseThrow(NoReleasesPublishedException::new);

        return LatestReleaseResponse.from(release, installedVersionCode);
    }

    @PostMapping("/{id}/download-link")
    public DownloadLinkResponse downloadLink(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        MobileRelease release = releaseService.findById(id);
        DownloadLinkService.IssuedDownloadLink issued = downloadLinkService.issue(release.getId(), jwt.getSubject());

        return new DownloadLinkResponse("/api/mobile/download/" + issued.token(), issued.expiresAt());
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<PublishReleaseResponse> publish(
            @RequestParam String versionName,
            @RequestParam int versionCode,
            @RequestParam String releaseNotes,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) Integer minSupportedVersionCode) {
        MobileRelease release = releaseService.publish(versionName, versionCode, releaseNotes, file,
                minSupportedVersionCode);

        return ResponseEntity.status(HttpStatus.CREATED).body(PublishReleaseResponse.from(release));
    }

    @ExceptionHandler(NoReleasesPublishedException.class)
    public ResponseEntity<ErrorBody> handleNoReleasesPublished(NoReleasesPublishedException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("NO_RELEASES_PUBLISHED"));
    }

    @ExceptionHandler(ReleaseNotFoundException.class)
    public ResponseEntity<ErrorBody> handleReleaseNotFound(ReleaseNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("RELEASE_NOT_FOUND"));
    }

    @ExceptionHandler(SigningCertMismatchException.class)
    public ResponseEntity<ErrorBody> handleSigningCertMismatch(SigningCertMismatchException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("SIGNING_CERT_MISMATCH"));
    }

    @ExceptionHandler(VersionCodeNotMonotonicException.class)
    public ResponseEntity<ErrorBody> handleVersionCodeNotMonotonic(VersionCodeNotMonotonicException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("VERSION_CODE_NOT_MONOTONIC"));
    }

    private record ErrorBody(String reason) {
    }
}
