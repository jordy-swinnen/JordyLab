package dev.jordy.jordylab.settings.rest.controller.model;

import dev.jordy.jordylab.settings.service.ManagedApp;

import java.util.Arrays;
import java.util.List;

/** The app registry the panel renders (spec 014 contract): what exists, what can be granted, what a new person gets. */
public record AppsResponse(List<AppRegistryItem> apps) {

    public static AppsResponse from(ManagedApp[] apps) {
        return new AppsResponse(Arrays.stream(apps)
                .map(app -> new AppRegistryItem(app.key(), app.displayName(), app.grantable(), app.defaultGrant(),
                        app.notGrantableReason()))
                .toList());
    }

    public record AppRegistryItem(String key, String name, boolean grantable, boolean defaultGrant, String reason) {
    }
}
