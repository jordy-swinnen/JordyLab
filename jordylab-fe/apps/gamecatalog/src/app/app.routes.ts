import { Route } from '@angular/router';
import { gamecatalogRoutes } from '@jordylab-fe/gamecatalog/ui';

/**
 * Standalone dev harness: serves the same routes the host mounts at /games, so
 * `nx serve gamecatalog` can exercise this domain without booting the host + Keycloak.
 */
export const appRoutes: Route[] = gamecatalogRoutes;
