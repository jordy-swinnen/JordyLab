package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
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
    private final GameRepository gameRepository;

    @Transactional(readOnly = true)
    public SourcesResponse listSources() {
        return new SourcesResponse(scanSourceRepository.findAll().stream()
                .map(source -> new ScanSourceResponse(source.getId(), source.getSourceKey(), source.getHostname(),
                        source.getSourceType(), source.getPlatform(), source.isEnabled(), source.getLastAttemptAt(),
                        source.getLastSuccessAt(), source.getLastOutcome(),
                        gameRepository.countInstalledBySourceId(source.getId())))
                .toList());
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
