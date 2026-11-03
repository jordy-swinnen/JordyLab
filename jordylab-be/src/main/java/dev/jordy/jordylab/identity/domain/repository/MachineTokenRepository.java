package dev.jordy.jordylab.identity.domain.repository;

import dev.jordy.jordylab.identity.domain.MachineToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MachineTokenRepository extends JpaRepository<MachineToken, UUID> {

    Optional<MachineToken> findByTokenHash(String tokenHash);

    List<MachineToken> findAllByOrderByCreatedDateDesc();
}
