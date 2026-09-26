import { Route } from '@angular/router';
import { authGuard, LoginComponent } from '@jordylab-fe/shared/auth';

export const appRoutes: Route[] = [
  { path: 'login', component: LoginComponent },
  {
    path: 'fna',
    canActivate: [authGuard],
    loadChildren: () =>
      import('@jordylab-fe/fna/ui').then((m) => m.fnaRoutes),
  },
  {
    path: 'games',
    canActivate: [authGuard],
    loadChildren: () =>
      import('@jordylab-fe/gamecatalog/ui').then((m) => m.gamecatalogRoutes),
  },
  { path: '', redirectTo: 'fna', pathMatch: 'full' },
];
