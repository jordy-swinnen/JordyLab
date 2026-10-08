package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.repository.HostRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HostResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Lets the admin give a machine a name of their own. The name lives on the {@link Host}, so it applies to every source
 * of that machine at once, and a later scan from the machine only ever finds the host by its hostname and never touches
 * the name (spec 013 FR-030, FR-031).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HostService {

    private final HostRepository hostRepository;
    private final PlaceNameService placeNameService;

    /** Sets, replaces or (blank) clears the display name; empty when the host does not exist. */
    @Transactional
    public Optional<HostResponse> setDisplayName(UUID id, String displayName) {
        return hostRepository.findById(id).map(host -> {
            host.rename(placeNameService.requireAvailableHostName(displayName, id));
            Host saved = hostRepository.save(host);
            log.info("Host '{}' is now shown as '{}'", saved.getHostname(), saved.label());

            return toResponse(saved);
        });
    }

    private HostResponse toResponse(Host host) {
        return new HostResponse(host.getId(), host.getHostname(), host.getDisplayName(), host.label());
    }
}
