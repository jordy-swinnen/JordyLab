package dev.jordy.jordylab.gamecatalog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * {@code jordylab.gamecatalog.autofill.*}: the background worker that fills in covers, facts, descriptions and the
 * search index without anyone pressing a button (spec 013 US4, research B7).
 */
@ConfigurationProperties(prefix = "jordylab.gamecatalog.autofill")
public record AutoFillProperties(int batchSize, int aiMaxAttempts, int freeLookupRetryHours, String sweepCron,
        String zone) {

    public AutoFillProperties {
        if (batchSize <= 0) {
            batchSize = 10;
        }
        if (aiMaxAttempts <= 0) {
            aiMaxAttempts = 3;
        }
        if (freeLookupRetryHours <= 0) {
            freeLookupRetryHours = 24;
        }
        sweepCron = StringUtils.hasText(sweepCron) ? sweepCron : "0 30 4 * * *";
        zone = StringUtils.hasText(zone) ? zone : "Europe/Brussels";
    }
}
