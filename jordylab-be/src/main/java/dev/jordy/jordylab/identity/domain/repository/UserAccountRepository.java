package dev.jordy.jordylab.identity.domain.repository;

import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    List<UserAccount> findAllByOrderByCreatedDateDesc();

    List<UserAccount> findByStatusOrderByCreatedDateDesc(UserStatus status);

    long countByRoleAndStatus(UserRole role, UserStatus status);
}
