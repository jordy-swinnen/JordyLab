package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.ChatRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ChatResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HostsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.RefreshAllResponse;
import dev.jordy.jordylab.gamecatalog.service.ArtworkService;
import dev.jordy.jordylab.gamecatalog.service.CatalogRefreshService;
import dev.jordy.jordylab.gamecatalog.service.ChatAttachmentException;
import dev.jordy.jordylab.gamecatalog.service.ChatService;
import dev.jordy.jordylab.gamecatalog.service.ChatUnavailableException;
import dev.jordy.jordylab.gamecatalog.service.GameQueryService;
import dev.jordy.jordylab.gamecatalog.service.MetadataNotSupportedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/gamecatalog")
@RequiredArgsConstructor
public class GameCatalogController {

    private static final int DEFAULT_PAGE_SIZE = 60;
    private static final int MAX_PAGE_SIZE = 200;

    private final GameQueryService gameQueryService;
    private final ArtworkService artworkService;
    private final ChatService chatService;
    private final CatalogRefreshService catalogRefreshService;

    @GetMapping("/games")
    public GamesPageResponse getGames(@RequestParam(required = false) String search,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String host,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "60") int size) {
        return gameQueryService.getGames(search, platform, host, Math.max(page, 0), clampPageSize(size));
    }

    @GetMapping("/platforms")
    public PlatformsResponse getPlatforms() {
        return gameQueryService.getPlatforms();
    }

    @GetMapping("/hosts")
    public HostsResponse getHosts() {
        return gameQueryService.getHosts();
    }

    @GetMapping("/games/{id}")
    public ResponseEntity<GameDetailResponse> getGameDetail(@PathVariable UUID id) {
        return gameQueryService.getGameDetail(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/games/{id}/metadata/refresh")
    public ResponseEntity<GameDetailResponse> refreshMetadata(@PathVariable UUID id) {
        return catalogRefreshService.refreshMetadata(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/games/{id}/enrichment/refresh")
    public ResponseEntity<GameDetailResponse> refreshEnrichment(@PathVariable UUID id) {
        return catalogRefreshService.refreshEnrichment(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/games/refresh")
    public RefreshAllResponse refreshPending() {
        return catalogRefreshService.refreshPending();
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.ask(request.question(), request.gameIds() == null ? List.of() : request.gameIds());
    }

    @ExceptionHandler(ChatUnavailableException.class)
    public ResponseEntity<ChatErrorBody> handleChatUnavailable(ChatUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ChatErrorBody("CHAT_UNAVAILABLE"));
    }

    @ExceptionHandler(ChatAttachmentException.class)
    public ResponseEntity<ChatErrorBody> handleInvalidAttachment(ChatAttachmentException exception) {
        return ResponseEntity.badRequest().body(new ChatErrorBody("GAME_IDS_INVALID"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ChatErrorBody> handleInvalidChatRequest(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(new ChatErrorBody("QUESTION_INVALID"));
    }

    @ExceptionHandler(MetadataNotSupportedException.class)
    public ResponseEntity<ChatErrorBody> handleMetadataNotSupported(MetadataNotSupportedException exception) {
        return ResponseEntity.badRequest().body(new ChatErrorBody("METADATA_NOT_SUPPORTED"));
    }

    private record ChatErrorBody(String reason) {
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

    private int clampPageSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }
}
