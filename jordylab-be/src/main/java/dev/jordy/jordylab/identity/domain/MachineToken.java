package dev.jordy.jordylab.identity.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A non-interactive credential for one scanning machine (spec 019 US5, D8). Only the SHA-256 hash of the
 * {@code jlmt_…} token is stored; the value is shown exactly once at creation. Revoked tokens are refused on the very
 * next call; {@code lastUsedAt} advances at most once a minute.
 */
@Entity
@Table(schema = "identity", name = "machine_token")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MachineToken extends BaseEntity<MachineToken> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MachineTokenScope scope;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public void revoke(Instant now) {
        Preconditions.checkArgument(now != null, "now is required");
        Preconditions.checkArgument(this.revokedAt == null, "token is already revoked");
        this.revokedAt = now;
    }

    public void touchLastUsed(Instant now) {
        Preconditions.checkArgument(now != null, "now is required");
        this.lastUsedAt = now;
    }

    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    public static class MachineTokenBuilder {
        public MachineToken build() {
            Preconditions.checkArgument(StringUtils.hasText(name), "name is required");
            Preconditions.checkArgument(StringUtils.hasText(tokenHash), "tokenHash is required");
            Preconditions.checkArgument(scope != null, "scope is required");
            Preconditions.checkArgument(createdBy != null, "createdBy is required");
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new MachineToken(id, name.trim(), tokenHash, scope, createdBy, lastUsedAt, revokedAt);
        }
    }
}
