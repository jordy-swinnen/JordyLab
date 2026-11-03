package dev.jordy.jordylab.identity.domain.repository;

import dev.jordy.jordylab.identity.domain.PasswordResetRequest;
import dev.jordy.jordylab.identity.domain.ResetRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, UUID> {

    Optional<PasswordResetRequest> findByUserAccountIdAndStatus(UUID userAccountId, ResetRequestStatus status);

    void deleteByUserAccountId(UUID userAccountId);
}
