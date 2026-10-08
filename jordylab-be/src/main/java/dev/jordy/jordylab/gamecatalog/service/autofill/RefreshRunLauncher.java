package dev.jordy.jordylab.gamecatalog.service.autofill;

/** Starts a refresh run's work away from the request that asked for it. Tests start it in place. */
public interface RefreshRunLauncher {

    void launch(Runnable work);
}
