package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.Host;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HostRepository extends JpaRepository<Host, UUID> {

    Optional<Host> findByHostnameIgnoreCase(String hostname);

    Optional<Host> findByDisplayNameIgnoreCase(String displayName);

    boolean existsByDisplayNameIgnoreCase(String displayName);
}
