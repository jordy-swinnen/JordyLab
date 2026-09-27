import { Route } from '@angular/router';
import { authGuard, LoginComponent, roleGuard } from '@jordylab-fe/shared/auth';
import { AwaitingApprovalComponent } from './awaiting-approval/awaiting-approval.component';

export const appRoutes: Route[] = [
  { path: 'login', component: LoginComponent },
  {
    path: 'awaiting-approval',
    canActivate: [authGuard],
    component: AwaitingApprovalComponent,
  },
  {
    path: 'fna',
    canActivate: [authGuard, roleGuard('admin')],
    loadChildren: () => import('@jordylab-fe/fna/ui').then((m) => m.fnaRoutes),
  },
  {
    path: 'games',
    canActivate: [authGuard, roleGuard('admin', 'guest')],
    loadChildren: () =>
      import('@jordylab-fe/gamecatalog/ui').then((m) => m.gamecatalogRoutes),
  },
  {
    path: 'settings',
    canActivate: [authGuard, roleGuard('admin')],
    loadChildren: () =>
      import('@jordylab-fe/settings/ui').then((m) => m.settingsRoutes),
  },
  { path: '', redirectTo: 'fna', pathMatch: 'full' },
];
