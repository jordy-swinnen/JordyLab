package dev.jordy.jordylab.mobile.rest.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jordy.jordylab.mobile.MobileProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves {@code /.well-known/assetlinks.json} for Android App Link verification (research §1.2,
 * D13). Must be {@code permitAll}, no redirects, exact {@code Content-Type: application/json} —
 * all satisfied by returning a plain {@code @RestController} body. {@code packageName} and the
 * signing-cert fingerprint are placeholders (research D14, stop-and-report gate) until the
 * application id and release keystore are finalized.
 */
@RestController
@RequiredArgsConstructor
public class AssetLinksController {

    private final MobileProperties properties;

    @GetMapping(value = "/.well-known/assetlinks.json", produces = "application/json")
    public List<AssetLinkStatement> assetLinks() {
        AssetLinkTarget target = new AssetLinkTarget("android_app", properties.app().packageName(),
                List.of(properties.release().signingCertSha256()));

        return List.of(new AssetLinkStatement(List.of("delegate_permission/common.handle_all_urls"), target));
    }

    public record AssetLinkStatement(List<String> relation, AssetLinkTarget target) {
    }

    public record AssetLinkTarget(
            String namespace,
            @JsonProperty("package_name") String packageName,
            @JsonProperty("sha256_cert_fingerprints") List<String> sha256CertFingerprints) {
    }
}
