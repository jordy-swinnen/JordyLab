import { Route } from '@angular/router';
import { loadRemoteModule } from '@softarc/native-federation-runtime';
import { authGuard } from './auth/auth.guard';
import { LoginComponent } from './auth/login.component';

export const appRoutes: Route[] = [
  { path: 'login', component: LoginComponent },
  {
    path: 'fna',
    canActivate: [authGuard],
    loadChildren: () =>
      loadRemoteModule('fna', './Routes').then((m) => m.appRoutes),
  },
  {
    path: 'games',
    canActivate: [authGuard],
    loadChildren: () =>
      loadRemoteModule('gamecatalog', './Routes').then((m) => m.appRoutes),
  },
  { path: '', redirectTo: 'fna', pathMatch: 'full' },
];
