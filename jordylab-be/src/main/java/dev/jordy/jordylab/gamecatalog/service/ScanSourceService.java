package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HideImpactResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanSourceResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourceEnabledResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourcesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanSourceService {

    private final ScanSourceRepository scanSourceRepository;
    private final GameInstallationRepository gameInstallationRepository;
    private final LibraryHealthService libraryHealthService;
    private final GameRepository gameRepository;

    @Transactional(readOnly = true)
    public SourcesResponse listSources() {
        return new SourcesResponse(scanSourceRepository.findAll().stream()
                .map(source -> new ScanSourceResponse(source.getId(), source.getSourceKey(), source.getHost().getId(),
                        source.getHost().getHostname(), source.getHost().getDisplayName(), source.hostLabel(),
                        source.getSourceType(), source.getPlatform(), PlatformChip.of(source.getPlatform()), source.isEnabled(), source.getLastAttemptAt(),
                        source.getLastSuccessAt(), source.getLastCheckedAt(), source.getLastOutcome(),
                        gameInstallationRepository.countInstalledBySourceId(source.getId())))
                .toList(), libraryHealthService.health());
    }

    /** Empty when the source does not exist; zeros when it is already off, because turning it off again changes nothing. */
    @Transactional(readOnly = true)
    public Optional<HideImpactResponse> hideImpact(UUID id) {
        return scanSourceRepository.findById(id).map(source -> {
            if (!source.isEnabled()) {
                return new HideImpactResponse(0, 0);
            }
            long held = gameRepository.countInstalledOnSource(id);
            long hidden = gameRepository.countHiddenIfSourceDisabled(id);

            return new HideImpactResponse(hidden, held - hidden);
        });
    }

    @Transactional
    public Optional<SourceEnabledResponse> setEnabled(UUID id, boolean enabled) {
        return scanSourceRepository.findById(id)
                .map(source -> {
                    source.setEnabled(enabled);
                    scanSourceRepository.save(source);
                    log.info("Scan source '{}' enabled state set to {}", source.getSourceKey(), enabled);

                    return new SourceEnabledResponse(source.getId(), source.isEnabled());
                });
    }
}
