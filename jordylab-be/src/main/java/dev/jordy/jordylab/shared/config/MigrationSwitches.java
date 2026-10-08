package dev.jordy.jordylab.shared.config;

import lombok.experimental.UtilityClass;

/**
 * Switches read by Flyway Java migrations. A Java migration runs before the Spring context exists, so it cannot use
 * {@code @ConfigurationProperties}: it reads a JVM system property, with the equivalent environment variable as a
 * second source (spec 013 T008, research B3).
 */
@UtilityClass
public class MigrationSwitches {

    /** {@code -Djordylab.migration.dry-run=true} or {@code JORDYLAB_MIGRATION_DRY_RUN=true}: plan the merge, then roll back. */
    public static final String DRY_RUN_PROPERTY = "jordylab.migration.dry-run";
    public static final String DRY_RUN_ENVIRONMENT_VARIABLE = "JORDYLAB_MIGRATION_DRY_RUN";

    public static boolean isDryRun() {
        String property = System.getProperty(DRY_RUN_PROPERTY);
        if (property != null) {
            return Boolean.parseBoolean(property);
        }

        return Boolean.parseBoolean(System.getenv(DRY_RUN_ENVIRONMENT_VARIABLE));
    }
}
