package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameFilter;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlacesResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformsResponse;
import dev.jordy.jordylab.gamecatalog.service.ArtworkService;
import dev.jordy.jordylab.gamecatalog.service.CatalogRefreshService;
import dev.jordy.jordylab.gamecatalog.service.GameQueryService;
import dev.jordy.jordylab.gamecatalog.service.MetadataNotSupportedException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/gamecatalog")
@RequiredArgsConstructor
public class GameCatalogController {

    private static final int DEFAULT_PAGE_SIZE = 60;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_LOCAL_PLAYERS = 8;
    private static final Set<String> INSTALL_STATUSES = Set.of("INSTALLED", "NOT_INSTALLED", "ALL");

    private final GameQueryService gameQueryService;
    private final ArtworkService artworkService;
    private final CatalogRefreshService catalogRefreshService;

    @GetMapping("/games")
    public GamesPageResponse getGames(@RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> platform,
            @RequestParam(required = false) List<UUID> where,
            @RequestParam(required = false, defaultValue = "INSTALLED") String installStatus,
            @RequestParam(required = false) List<GameSource> source,
            @RequestParam(required = false) @Min(1) @Max(MAX_LOCAL_PLAYERS) Integer minLocalPlayers,
            @RequestParam(required = false) List<RomStatus> romStatus,
            @RequestParam(required = false) List<MarkType> mark,
            @RequestParam(required = false) GameFilter.MarkScope markScope,
            @RequestParam(required = false) GameFilter.Sort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "60") int size,
            @AuthenticationPrincipal Jwt jwt) {
        GameFilter filter = GameFilter.builder()
                .search(normalizeText(search))
                .platforms(platform)
                .whereIds(where)
                .installStatus(normalizeInstallStatus(installStatus))
                .sources(source)
                .minLocalPlayers(minLocalPlayers)
                .romStatuses(romStatus)
                .marks(mark)
                .markScope(markScope)
                .userSubject(subjectOf(jwt))
                .sort(sort)
                .build();

        return gameQueryService.getGames(filter, Math.max(page, 0), clampPageSize(size));
    }

    @GetMapping("/platforms")
    public PlatformsResponse getPlatforms() {
        return gameQueryService.getPlatforms();
    }

    @GetMapping("/places")
    public PlacesResponse getPlaces() {
        return gameQueryService.getPlaces();
    }

    @GetMapping("/games/{id}")
    public ResponseEntity<GameDetailResponse> getGameDetail(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return gameQueryService.getGameDetail(id, subjectOf(jwt))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/games/{id}/metadata/refresh")
    public ResponseEntity<GameDetailResponse> refreshMetadata(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return catalogRefreshService.refreshMetadata(id, subjectOf(jwt))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/games/{id}/enrichment/refresh")
    public ResponseEntity<GameDetailResponse> refreshEnrichment(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return catalogRefreshService.refreshEnrichment(id, subjectOf(jwt))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/games/{id}/multiplayer/refresh")
    public ResponseEntity<GameDetailResponse> refreshMultiplayer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return catalogRefreshService.refreshMultiplayer(id, subjectOf(jwt))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/games/{id}/artwork")
    public ResponseEntity<byte[]> getArtwork(@PathVariable UUID id) {
        return artworkService.loadVisibleArtwork(id)
                .map(content -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(content.mediaType()))
                        .header("X-Content-Type-Options", "nosniff")
                        .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                        .body(content.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(MetadataNotSupportedException.class)
    public ResponseEntity<ErrorBody> handleMetadataNotSupported(MetadataNotSupportedException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("METADATA_NOT_SUPPORTED"));
    }

    private String subjectOf(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }

    private String normalizeText(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String normalizeInstallStatus(String installStatus) {
        String normalized = installStatus == null ? "" : installStatus.trim().toUpperCase(Locale.ROOT);

        return INSTALL_STATUSES.contains(normalized) ? normalized : "INSTALLED";
    }

    private int clampPageSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    private record ErrorBody(String reason) {
    }
}
