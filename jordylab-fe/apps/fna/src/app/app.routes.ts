import { Route } from '@angular/router';
import { fnaRoutes } from '@jordylab-fe/fna/ui';

/**
 * Standalone dev harness: serves the same routes the host mounts at /fna, so
 * `nx serve fna` can exercise this domain without booting the host + Keycloak.
 */
export const appRoutes: Route[] = fnaRoutes;
