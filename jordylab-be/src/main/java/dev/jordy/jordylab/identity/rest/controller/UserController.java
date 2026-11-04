package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.CurrentUserResolver;
import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.domain.PasswordResetRequest;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.rest.controller.model.ApproveRequest;
import dev.jordy.jordylab.identity.rest.controller.model.PendingCountResponse;
import dev.jordy.jordylab.identity.rest.controller.model.PendingResetRequestView;
import dev.jordy.jordylab.identity.rest.controller.model.ResetPasswordResponse;
import dev.jordy.jordylab.identity.rest.controller.model.UserView;
import dev.jordy.jordylab.identity.rest.controller.model.SessionView;
import dev.jordy.jordylab.identity.rest.controller.model.SessionsResponse;
import dev.jordy.jordylab.identity.rest.controller.model.UsersResponse;
import dev.jordy.jordylab.identity.service.InvalidRequestException;
import dev.jordy.jordylab.identity.service.PasswordResetService;
import dev.jordy.jordylab.identity.service.UserAccountService;
import dev.jordy.jordylab.identity.service.UserDeletionService;
import dev.jordy.jordylab.identity.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

/**
 * Admin user management over the identity module (spec 019 US1/US4, parity with spec 014's panel).
 * {@code /api/identity/users/**} already requires {@code admin} in the security configuration.
 * App grants, sessions, deletion and reset requests land with US4/US6.
 */
@RestController
@RequestMapping("/api/identity/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAccountService userAccountService;
    private final UserSessionService userSessionService;
    private final UserDeletionService userDeletionService;
    private final PasswordResetService passwordResetService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public UsersResponse getUsers(@RequestParam(required = false, defaultValue = "ALL") String status) {
        java.util.Map<UUID, PasswordResetRequest> pending = passwordResetService.pendingByUser();

        return new UsersResponse(userAccountService.listUsers(parseStatus(status)).stream()
                .map(account -> UserView.from(account, pendingRequest(pending, account)))
                .toList());
    }

    @GetMapping("/pending-count")
    public PendingCountResponse getPendingCount() {
        return new PendingCountResponse(userAccountService.pendingCount());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id, @RequestBody(required = false) ApproveRequest request) {
        userAccountService.approve(id, parseRole(request == null ? null : request.role()));

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id) {
        userAccountService.reject(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        userAccountService.revoke(id);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/apps/{app}")
    public ResponseEntity<Void> grantApp(@PathVariable UUID id, @PathVariable String app) {
        userAccountService.grantApp(id, requireGrantableApp(app));

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/apps/{app}")
    public ResponseEntity<Void> revokeApp(@PathVariable UUID id, @PathVariable String app) {
        userAccountService.revokeApp(id, requireGrantableApp(app));

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/sessions")
    public SessionsResponse sessions(@PathVariable UUID id) {
        return new SessionsResponse(userSessionService.list(callerId(), id).stream()
                .map(session -> new SessionView(session.sessionId(), session.startedAt(), session.lastAccessedAt()))
                .toList());
    }

    @PostMapping("/{id}/sessions/{sessionId}/end")
    public ResponseEntity<Void> endSession(@PathVariable UUID id, @PathVariable String sessionId) {
        userSessionService.endOne(callerId(), id, sessionId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/sessions/end-all")
    public ResponseEntity<Void> endAllSessions(@PathVariable UUID id) {
        userSessionService.endAll(callerId(), id);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePerson(@PathVariable UUID id) {
        userDeletionService.delete(callerId(), id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@PathVariable UUID id) {
        return new ResetPasswordResponse(passwordResetService.resetDirectly(id));
    }

    @PostMapping("/{id}/reset-requests/{requestId}/approve")
    public ResponseEntity<Void> approveResetRequest(@PathVariable UUID id, @PathVariable UUID requestId) {
        passwordResetService.approve(callerId(), id, requestId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-requests/{requestId}/reject")
    public ResponseEntity<Void> rejectResetRequest(@PathVariable UUID id, @PathVariable UUID requestId) {
        passwordResetService.reject(callerId(), id, requestId);

        return ResponseEntity.noContent().build();
    }

    private static PendingResetRequestView pendingRequest(
            java.util.Map<UUID, PasswordResetRequest> pending, UserAccount account) {
        PasswordResetRequest request = pending.get(account.getId());

        return request == null ? null : new PendingResetRequestView(request.getId(), request.getCreatedDate());
    }

    private UUID callerId() {
        return UUID.fromString(currentUserResolver.requireCurrentUserSubject());
    }

    private static ManagedApp requireGrantableApp(String appKey) {
        ManagedApp app = ManagedApp.fromKey(appKey)
                .orElseThrow(() -> new InvalidRequestException("Unknown app: " + appKey));
        if (!app.grantable()) {
            throw new InvalidRequestException("That app cannot be granted");
        }

        return app;
    }

    private static UserStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return UserStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Unknown status: " + status);
        }
    }

    private static UserRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return UserRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Unknown role: " + role);
        }
    }
}
