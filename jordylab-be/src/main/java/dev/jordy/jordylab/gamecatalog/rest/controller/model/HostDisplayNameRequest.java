package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/** {@code displayName} null or blank clears the name so the hostname is shown again. */
public record HostDisplayNameRequest(String displayName) {
}
