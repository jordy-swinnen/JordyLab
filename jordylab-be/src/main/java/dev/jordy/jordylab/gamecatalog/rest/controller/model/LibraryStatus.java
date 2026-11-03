package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/** The Steam library syncs and whether a catalog refresh is running right now. */
public record LibraryStatus(LibrarySyncStatus steamOwned, LibrarySyncStatus steamFamily, boolean ownedConfigured,
                            boolean refreshRunning) {
}
