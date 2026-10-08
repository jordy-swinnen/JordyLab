package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.Console;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsoleRepository extends JpaRepository<Console, UUID> {

    List<Console> findAllByOrderByNameAsc();

    Optional<Console> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
