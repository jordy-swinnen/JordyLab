package dev.jordy.jordylab.gamecatalog.service.autofill;

import dev.jordy.jordylab.gamecatalog.CatalogChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * When the auto-fill runs (spec 013 FR-019): right after the catalog changed, once at start-up (a restart picks up
 * whatever was left), and every day at the configured time (anything that failed for lack of a source heals by itself).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoFillTrigger {

    private final CatalogAutoFillService autoFillService;

    @ApplicationModuleListener
    void on(CatalogChanged event) {
        log.debug("Catalog changed ({}): running auto-fill", event.reason());
        autoFillService.run();
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        Thread.ofVirtual().name("autofill-startup").start(autoFillService::run);
    }

    @Scheduled(cron = "${jordylab.gamecatalog.autofill.sweep-cron:0 30 4 * * *}",
            zone = "${jordylab.gamecatalog.autofill.zone:Europe/Brussels}")
    void dailySweep() {
        autoFillService.run();
    }
}
