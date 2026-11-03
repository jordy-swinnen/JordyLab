package dev.jordy.jordylab.settings.rest.controller.model;

import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.settings.service.AppUser;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String email, String firstName, String lastName, Instant createdAt,
        boolean enabled, List<String> realmRoles, String status, boolean isAdmin, List<AppView> apps,
        PersonChatLimit chatLimit, Integer sessionCount) {

    public static UserResponse from(AppUser user, PersonChatLimit chatLimit, Integer sessionCount) {
        Set<String> roles = user.realmRoles();

        return new UserResponse(user.id(), user.email(), user.firstName(), user.lastName(), user.createdAt(),
                user.enabled(), List.copyOf(roles), user.status().name(), roles.contains("admin"), appViews(roles),
                chatLimit, sessionCount);
    }

    private static List<AppView> appViews(Set<String> roles) {
        return Arrays.stream(ManagedApp.values())
                .map(app -> AppView.of(app, roles))
                .toList();
    }
}
