package dev.jordy.jordylab.identity.rest.controller.model;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
