package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.rest.controller.model.ApproveRequest;
import dev.jordy.jordylab.identity.rest.controller.model.PendingCountResponse;
import dev.jordy.jordylab.identity.rest.controller.model.UserView;
import dev.jordy.jordylab.identity.rest.controller.model.UsersResponse;
import dev.jordy.jordylab.identity.service.InvalidRequestException;
import dev.jordy.jordylab.identity.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @GetMapping
    public UsersResponse getUsers(@RequestParam(required = false, defaultValue = "ALL") String status) {
        return new UsersResponse(userAccountService.listUsers(parseStatus(status)).stream()
                .map(UserView::from)
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
