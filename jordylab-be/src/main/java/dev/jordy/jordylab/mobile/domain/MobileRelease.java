package dev.jordy.jordylab.mobile.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * One published, signed Android release (spec FR-001, data-model.md). Written once by the CI
 * publish call; a correction is a new release with a higher {@code versionCode}, never an
 * in-place edit — this entity has no mutation methods.
 */
@Entity
@Table(schema = "mobile", name = "mobile_release")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MobileRelease extends BaseEntity<MobileRelease> {

    private static final int SHA256_HEX_LENGTH = 64;

    @Id
    private UUID id;

    @Column(name = "version_name")
    private String versionName;

    @Column(name = "version_code")
    private int versionCode;

    @Column(name = "release_notes")
    private String releaseNotes;

    private String sha256;

    @Column(name = "size_bytes")
    private long sizeBytes;

    @Column(name = "min_supported_version_code")
    private int minSupportedVersionCode;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "published_at")
    private Instant publishedAt;

    public static class MobileReleaseBuilder {
        public MobileRelease build() {
            Preconditions.checkArgument(StringUtils.hasText(versionName), "versionName is required");
            Preconditions.checkArgument(versionCode > 0, "versionCode must be positive");
            Preconditions.checkArgument(StringUtils.hasText(releaseNotes), "releaseNotes is required");
            Preconditions.checkArgument(sha256 != null && sha256.length() == SHA256_HEX_LENGTH,
                    "sha256 must be exactly 64 hex characters");
            Preconditions.checkArgument(sizeBytes > 0, "sizeBytes must be positive");
            Preconditions.checkArgument(minSupportedVersionCode > 0, "minSupportedVersionCode must be positive");
            Preconditions.checkArgument(minSupportedVersionCode <= versionCode,
                    "minSupportedVersionCode must not exceed this release's own versionCode");
            Preconditions.checkArgument(StringUtils.hasText(storageKey), "storageKey is required");
            Preconditions.checkArgument(publishedAt != null, "publishedAt is required");
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new MobileRelease(id, versionName, versionCode, releaseNotes, sha256, sizeBytes,
                    minSupportedVersionCode, storageKey, publishedAt);
        }
    }
}
