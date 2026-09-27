package dev.jordy.jordylab.settings.rest.controller.model;

import dev.jordy.jordylab.settings.service.AppUser;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(UUID id, String email, String firstName, String lastName, Instant createdAt,
        boolean enabled, List<String> realmRoles, String status) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.id(), user.email(), user.firstName(), user.lastName(), user.createdAt(),
                user.enabled(), List.copyOf(user.realmRoles()), user.status().name());
    }
}
