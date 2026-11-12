package dev.jordy.jordylab.identity.rest.controller.model;

/**
 * Sets the account-wide theme; an unknown value falls back to following the device.
 */
public record ThemePreferenceRequest(String theme) {
}
