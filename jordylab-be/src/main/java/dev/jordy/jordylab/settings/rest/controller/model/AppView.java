package dev.jordy.jordylab.settings.rest.controller.model;

import dev.jordy.jordylab.identity.ManagedApp;

import java.util.Set;

/** One app as it applies to one person: whether they have it, whether it can be given, and why not when it cannot. */
public record AppView(String key, String name, boolean granted, boolean grantable, String reason) {

    public static AppView of(ManagedApp app, Set<String> realmRoles) {
        boolean granted = app.grantRole().map(realmRoles::contains).orElse(false);

        return new AppView(app.key(), app.displayName(), granted, app.grantable(), app.notGrantableReason());
    }
}
