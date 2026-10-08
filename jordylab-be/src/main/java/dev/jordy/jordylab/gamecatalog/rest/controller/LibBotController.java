package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibBotAnswerResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibBotAskRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibBotErrorResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.service.GameQueryService;
import dev.jordy.jordylab.gamecatalog.service.libbot.Candidate;
import dev.jordy.jordylab.gamecatalog.service.libbot.ConversationStore;
import dev.jordy.jordylab.gamecatalog.service.libbot.LibBotAttachmentException;
import dev.jordy.jordylab.gamecatalog.service.libbot.LibBotResult;
import dev.jordy.jordylab.gamecatalog.service.libbot.LibBotService;
import dev.jordy.jordylab.gamecatalog.service.libbot.LibBotStage;
import dev.jordy.jordylab.gamecatalog.service.libbot.LibBotUnavailableException;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LibBot over Server-Sent Events (spec 013 research A8, contracts/libbot-api.md): {@code stage} events while it works,
 * then exactly one {@code answer} or one {@code error}. A heartbeat comment keeps proxies open. The guest allowance is
 * pre-checked before the stream opens (settings module) and counted only after an answer exists. The allowance itself
 * is served by the settings module at {@code GET /api/gamecatalog/libbot/quota}.
 */
@Slf4j
@RestController
@RequestMapping("/api/gamecatalog/libbot")
@RequiredArgsConstructor
public class LibBotController {

    private static final long STREAM_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(3);
    private static final long HEARTBEAT_SECONDS = 15;
    private static final String ADMIN_AUTHORITY = "ROLE_admin";

    private final LibBotService libBotService;
    private final ConversationStore conversationStore;
    private final GameQueryService gameQueryService;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor();

    @PostMapping(path = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@Valid @RequestBody LibBotAskRequest request, @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        libBotService.requireAttachable(request.attachedGameIds(), jwt.getSubject());
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        LibBotService.Ask ask = new LibBotService.Ask(jwt.getSubject(), admin, request.conversationId(),
                request.message(), request.attachedGameIds());
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        AtomicBoolean connected = new AtomicBoolean(true);
        emitter.onCompletion(() -> connected.set(false));
        emitter.onTimeout(() -> connected.set(false));
        emitter.onError(error -> connected.set(false));
        ScheduledFuture<?> heartbeat = heartbeats.scheduleAtFixedRate(() -> ping(emitter, connected),
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        workers.execute(() -> run(ask, emitter, connected, heartbeat));

        return emitter;
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> forget(@PathVariable String conversationId, @AuthenticationPrincipal Jwt jwt) {
        conversationStore.clear(jwt.getSubject(), conversationId);

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(LibBotAttachmentException.class)
    public ResponseEntity<ErrorBody> handleInvalidAttachment() {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_ATTACHMENT"));
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
        heartbeats.shutdownNow();
    }

    private void run(LibBotService.Ask ask, SseEmitter emitter, AtomicBoolean connected, ScheduledFuture<?> heartbeat) {
        try {
            LibBotResult result = libBotService.ask(ask, stage -> send(emitter, connected, "stage",
                    Map.of("stage", stage.name())), connected::get);
            if (connected.get()) {
                send(emitter, connected, "answer", toResponse(result));
            }
        } catch (LibBotUnavailableException exception) {
            log.warn("LibBot could not answer: {}", exception.getMessage());
            send(emitter, connected, "error", new LibBotErrorResponse("UNAVAILABLE", true));
        } catch (RuntimeException exception) {
            log.error("LibBot failed", exception);
            send(emitter, connected, "error", new LibBotErrorResponse("INTERNAL", true));
        } finally {
            heartbeat.cancel(false);
            emitter.complete();
        }
    }

    private LibBotAnswerResponse toResponse(LibBotResult result) {
        Map<UUID, GameSummaryResponse> summaries = gameQueryService.getSummaries(
                result.references().stream().map(Candidate::gameId).toList());
        List<LibBotAnswerResponse.Reference> references = result.references().stream()
                .filter(candidate -> summaries.containsKey(candidate.gameId()))
                .map(candidate -> {
                    GameSummaryResponse summary = summaries.get(candidate.gameId());

                    return new LibBotAnswerResponse.Reference(candidate.gameId(), candidate.title(),
                            summary.platforms(), new LibBotAnswerResponse.Cover(summary.coverStatus(),
                                    summary.coverUrl(), summary.coverEndpoint()));
                }).toList();

        return new LibBotAnswerResponse(result.outcome(), result.language().code(), result.text(), result.applied(),
                result.unknown() == null ? null
                        : new LibBotAnswerResponse.Unknown(result.unknown().count(), result.unknown().note()),
                references);
    }

    private void send(SseEmitter emitter, AtomicBoolean connected, String name, Object data) {
        if (!connected.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException exception) {
            connected.set(false);
        }
    }

    private void ping(SseEmitter emitter, AtomicBoolean connected) {
        if (!connected.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().comment("ping"));
        } catch (IOException | IllegalStateException exception) {
            connected.set(false);
        }
    }

    public record ErrorBody(String reason) {
    }
}
