package dev.jordy.jordylab.gamecatalog.service.autofill;

import org.springframework.stereotype.Component;

/** A refresh run is long and mostly waiting on other services, so it gets its own virtual thread. */
@Component
class VirtualThreadRefreshRunLauncher implements RefreshRunLauncher {

    @Override
    public void launch(Runnable work) {
        Thread.ofVirtual().name("refresh-run").start(work);
    }
}
