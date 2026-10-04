import { inject } from '@angular/core';
import { CanActivateFn, Route, Router } from '@angular/router';
import { PlatformService } from '@jordylab-fe/shared/platform/api';
import { authGuard, LoginComponent, roleGuard } from '@jordylab-fe/shared/auth';
import { ShareLandingComponent } from '@jordylab-fe/shared/platform/ui';
import { AppSettingsPageComponent } from './app-settings/app-settings-page.component';
import { AwaitingApprovalComponent } from './awaiting-approval/awaiting-approval.component';

/** Settings → App only exists inside the native app; on the web the URL goes home. */
const nativeOnlyGuard: CanActivateFn = () =>
  inject(PlatformService).isNative() || inject(Router).parseUrl('/');

export const appRoutes: Route[] = [
  { path: 'login', component: LoginComponent },
  {
    path: 'awaiting-approval',
    canActivate: [authGuard],
    component: AwaitingApprovalComponent,
  },
  {
    // Spec US5 — ShareTargetService navigates here on a native shareReceived event.
    // authGuard (not roleGuard) so a pending/logged-out share still runs login first (US5-4);
    // the landing screen itself role-filters its destinations.
    path: 'mobile/share',
    canActivate: [authGuard],
    component: ShareLandingComponent,
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
    // Declared before the admin-only `settings` area so guests can reach their own device setting too.
    path: 'settings/app',
    canActivate: [nativeOnlyGuard, authGuard, roleGuard('admin', 'guest')],
    component: AppSettingsPageComponent,
  },
  {
    path: 'settings',
    canActivate: [authGuard, roleGuard('admin')],
    loadChildren: () =>
      import('@jordylab-fe/settings/ui').then((m) => m.settingsRoutes),
  },
  { path: '', redirectTo: 'fna', pathMatch: 'full' },
];
