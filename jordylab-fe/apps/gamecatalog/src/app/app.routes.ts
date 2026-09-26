import { Route } from '@angular/router';
import { authGuard, LoginComponent } from '@jordylab-fe/shared/auth';
import { gamecatalogRoutes } from '@jordylab-fe/gamecatalog/ui';

/**
 * Standalone dev harness: serves the same routes the host mounts at /games, so
 * `nx serve gamecatalog` can exercise this domain without booting the host shell. It does still
 * authenticate against the real Keycloak realm — see AUTH_CONFIG in app.config.ts.
 */
export const appRoutes: Route[] = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    canActivate: [authGuard],
    children: gamecatalogRoutes,
  },
];
