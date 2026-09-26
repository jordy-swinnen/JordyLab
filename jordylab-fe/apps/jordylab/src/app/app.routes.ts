import { Route } from '@angular/router';
import { authGuard } from './auth/auth.guard';
import { LoginComponent } from './auth/login.component';

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
