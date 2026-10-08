package db.migration;

import dev.jordy.jordylab.gamecatalog.util.GameIdentityBackfill;
import dev.jordy.jordylab.shared.config.MigrationSwitches;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * Spec 013 migration 2 of 3: canonical platform names, {@code title_key}, and the merge of duplicate games. All the
 * logic lives in {@link GameIdentityBackfill} so tests and the application share it. With the dry-run switch on
 * ({@code -Djordylab.migration.dry-run=true}) the plan is logged and the migration fails on purpose, so Flyway rolls the
 * whole transaction back and nothing changes.
 */
public class V20261008002__GameIdentityBackfillAndMerge extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("SET search_path TO gamecatalog, public");
        }
        GameIdentityBackfill.Result result = new GameIdentityBackfill().run(context.getConnection());
        if (MigrationSwitches.isDryRun()) {
            throw new IllegalStateException("Dry run complete, rolling back on purpose: " + result.merges().size()
                    + " merge(s) planned, " + result.titleKeysSet() + " title key(s), " + result.platformsRenamed()
                    + " platform name(s) canonicalised. See the 'Merged' log lines above, then run without "
                    + MigrationSwitches.DRY_RUN_PROPERTY + ".");
        }
    }
}
