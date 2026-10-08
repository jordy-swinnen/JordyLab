package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlaceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Whether the ROM of one emulated copy launches (spec 013 US13). The status belongs to that copy on that machine, is public
 * to everyone who may see the game, and the last write wins. It is kept on the installation, which a rescan only marks seen
 * again or uninstalled, so it survives rescans and the grace period.
 */
@Service
@RequiredArgsConstructor
public class RomStatusService {

    private final GameRepository gameRepository;
    private final GameInstallationRepository installationRepository;

    @Transactional
    public PlaceResponse setStatus(UUID gameId, UUID installationId, RomStatus status) {
        if (gameRepository.findVisibleById(gameId).isEmpty()) {
            throw new InstallationNotFoundException();
        }
        GameInstallation installation = installationRepository.findByIdAndGameId(installationId, gameId)
                .filter(candidate -> candidate.getSource().isEnabled())
                .orElseThrow(InstallationNotFoundException::new);
        if (!installation.isEmulated()) {
            throw new RomStatusNotApplicableException();
        }
        installation.changeRomStatus(status);

        return PlaceResponse.hostCopy(installationRepository.save(installation));
    }
}
