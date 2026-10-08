package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Console;
import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.HostRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Keeps the names people see unique across hosts AND consoles, ignoring letter case (spec 013 FR-031). Two tables cannot
 * share one database index, so the check runs under a transaction-scoped advisory lock: two admins naming things at the
 * same moment cannot both win. The name a host shows is its display name when set, otherwise its hostname, so a new name
 * is compared against both.
 */
@Service
@RequiredArgsConstructor
public class PlaceNameService {

    private static final String LOCK_KEY = "gamecatalog-place-name";

    private final HostRepository hostRepository;
    private final ConsoleRepository consoleRepository;
    private final EntityManager entityManager;

    /** Fails when {@code name} is too long or taken by a host or console other than {@code ownId}; returns the trimmed name. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String requireAvailable(String name, UUID ownId, int maxLength) {
        String trimmed = StringUtils.hasText(name) ? name.trim() : "";
        if (trimmed.length() > maxLength) {
            throw new NameTooLongException(maxLength);
        }
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(hashtextextended(:key, 0)) as text)")
                .setParameter("key", LOCK_KEY)
                .getSingleResult();
        boolean usedByHost = hostRepository.findAll().stream()
                .filter(host -> !host.getId().equals(ownId))
                .anyMatch(host -> sameName(host, trimmed));
        boolean usedByConsole = consoleRepository.findAll().stream()
                .filter(console -> !console.getId().equals(ownId))
                .anyMatch(console -> console.getName().equalsIgnoreCase(trimmed));
        if (usedByHost || usedByConsole) {
            throw new NameTakenException(trimmed);
        }

        return trimmed;
    }

    private boolean sameName(Host host, String name) {
        return host.label().equalsIgnoreCase(name) || host.getHostname().equalsIgnoreCase(name);
    }

    /** Convenience for console naming with the console's own length limit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String requireAvailableConsoleName(String name, UUID ownId) {
        return requireAvailable(name, ownId, Console.MAX_NAME_LENGTH);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String requireAvailableHostName(String name, UUID ownId) {
        return requireAvailable(name, ownId, Host.MAX_DISPLAY_NAME_LENGTH);
    }
}
