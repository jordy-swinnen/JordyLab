import { Route } from '@angular/router';
import { authGuard, LoginComponent, RegisterComponent } from '@jordylab-fe/shared/auth';
import { gamecatalogRoutes } from '@jordylab-fe/gamecatalog/ui';

/**
 * Standalone dev harness: serves the same routes the host mounts at /games, so
 * `nx serve gamecatalog` can exercise this domain without booting the host shell. It does still
 */
export const appRoutes: Route[] = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  {
    path: '',
    canActivate: [authGuard],
    children: gamecatalogRoutes,
  },
];
