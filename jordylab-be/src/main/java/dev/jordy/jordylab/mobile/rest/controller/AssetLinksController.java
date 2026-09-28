package dev.jordy.jordylab.mobile.rest.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jordy.jordylab.mobile.MobileProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Serves {@code /.well-known/assetlinks.json} for Android App Link verification (research §1.2,
 * D13). Must be {@code permitAll}, no redirects, exact {@code Content-Type: application/json} —
 * all satisfied by returning a plain {@code @RestController} body. {@code packageName} and the
 * signing-cert fingerprint are placeholders (research D14, stop-and-report gate) until the
 * application id and release keystore are finalized.
 *
 * <p>{@code jordylab.mobile.release.signing-cert-sha256} is stored as plain (unbroken) hex — the
 * same format {@link dev.jordy.jordylab.mobile.util.ApkSigningCertificateReader} produces and
 * {@link dev.jordy.jordylab.mobile.service.MobileReleaseService} compares against at publish
 * time. The Digital Asset Links spec requires {@code sha256_cert_fingerprints} as
 * colon-separated hex (contracts/mobile-releases-api.md), so the colons are inserted here, at
 * the boundary, rather than changing the stored format both other consumers rely on.
 */
@RestController
@RequiredArgsConstructor
public class AssetLinksController {

    private static final int HEX_PAIR_LENGTH = 2;

    private final MobileProperties properties;

    @GetMapping(value = "/.well-known/assetlinks.json", produces = "application/json")
    public List<AssetLinkStatement> assetLinks() {
        AssetLinkTarget target = new AssetLinkTarget("android_app", properties.app().packageName(),
                List.of(colonSeparate(properties.release().signingCertSha256())));

        return List.of(new AssetLinkStatement(List.of("delegate_permission/common.handle_all_urls"), target));
    }

    private static String colonSeparate(String plainHex) {
        StringBuilder colonSeparated = new StringBuilder(plainHex.length() + plainHex.length() / HEX_PAIR_LENGTH);
        for (int index = 0; index < plainHex.length(); index += HEX_PAIR_LENGTH) {
            if (index > 0) {
                colonSeparated.append(':');
            }
            colonSeparated.append(plainHex, index, Math.min(index + HEX_PAIR_LENGTH, plainHex.length()));
        }

        return colonSeparated.toString().toUpperCase(Locale.ROOT);
    }

    public record AssetLinkStatement(List<String> relation, AssetLinkTarget target) {
    }

    public record AssetLinkTarget(
            String namespace,
            @JsonProperty("package_name") String packageName,
            @JsonProperty("sha256_cert_fingerprints") List<String> sha256CertFingerprints) {
    }
}
