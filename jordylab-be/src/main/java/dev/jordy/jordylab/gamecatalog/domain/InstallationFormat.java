package dev.jordy.jordylab.gamecatalog.domain;

/**
 * Physical or digital form of a manually tracked game installation (feature 009).
 * Only used when {@link GameInstallation#isManual()} is true; scanner-created
 * installations do not set a format.
 */
public enum InstallationFormat {
    PHYSICAL,
    DIGITAL
}
