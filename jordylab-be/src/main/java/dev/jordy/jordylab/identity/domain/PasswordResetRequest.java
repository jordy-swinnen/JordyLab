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
 * A locked-out user's request for a new password (spec 019 US6, D15). Only the BCrypt hash of the user-chosen password
 * is stored — the admin approving it never sees it, and the plaintext exists only in the request body.
 */
@Entity
@Table(schema = "identity", name = "password_reset_request")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetRequest extends BaseEntity<PasswordResetRequest> {

    @Id
    private UUID id;

    @Column(name = "user_account_id", nullable = false)
    private UUID userAccountId;

    @Column(name = "requested_password_hash", nullable = false)
    private String requestedPasswordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResetRequestStatus status;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    public void approve(UUID decidingAdminId, Instant now) {
        requirePending();
        Preconditions.checkArgument(decidingAdminId != null, "decidedBy is required");
        Preconditions.checkArgument(now != null, "now is required");
        this.status = ResetRequestStatus.APPROVED;
        this.decidedBy = decidingAdminId;
        this.decidedAt = now;
    }

    public void reject(UUID decidingAdminId, Instant now) {
        requirePending();
        Preconditions.checkArgument(decidingAdminId != null, "decidedBy is required");
        Preconditions.checkArgument(now != null, "now is required");
        this.status = ResetRequestStatus.REJECTED;
        this.decidedBy = decidingAdminId;
        this.decidedAt = now;
    }

    public boolean isPending() {
        return this.status == ResetRequestStatus.PENDING;
    }

    private void requirePending() {
        Preconditions.checkArgument(isPending(), "request is not pending");
    }

    public static class PasswordResetRequestBuilder {
        public PasswordResetRequest build() {
            Preconditions.checkArgument(userAccountId != null, "userAccountId is required");
            Preconditions.checkArgument(StringUtils.hasText(requestedPasswordHash),
                    "requestedPasswordHash is required");
            if (status == null) {
                status = ResetRequestStatus.PENDING;
            }
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new PasswordResetRequest(id, userAccountId, requestedPasswordHash, status, decidedAt, decidedBy);
        }
    }
}
