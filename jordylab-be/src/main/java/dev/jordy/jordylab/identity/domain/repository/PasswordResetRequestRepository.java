package dev.jordy.jordylab.identity.domain.repository;

import dev.jordy.jordylab.identity.domain.PasswordResetRequest;
import dev.jordy.jordylab.identity.domain.ResetRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, UUID> {

    Optional<PasswordResetRequest> findByUserAccountIdAndStatus(UUID userAccountId, ResetRequestStatus status);

    List<PasswordResetRequest> findByStatus(ResetRequestStatus status);

    long countByStatus(ResetRequestStatus status);

    void deleteByUserAccountId(UUID userAccountId);

    void deleteByUserAccountIdAndStatus(UUID userAccountId, ResetRequestStatus status);
}
