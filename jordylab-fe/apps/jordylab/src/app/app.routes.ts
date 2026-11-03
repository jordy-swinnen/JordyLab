import { Route } from '@angular/router';
import { authGuard, LoginComponent, roleGuard } from '@jordylab-fe/shared/auth';
import { ShareLandingComponent } from '@jordylab-fe/shared/platform/ui';
import { settingsAdminRoutes } from '@jordylab-fe/settings/ui';
import { AppSettingsPageComponent } from './app-settings/app-settings-page.component';
import { AwaitingApprovalComponent } from './awaiting-approval/awaiting-approval.component';
import { SettingsHomeComponent } from './settings-home/settings-home.component';

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
    // Declared before the admin-only `settings` area so guests can reach their own device setting too, on the web and in the app.
    path: 'settings/app',
    canActivate: [authGuard, roleGuard('admin', 'guest')],
    component: AppSettingsPageComponent,
  },
  {
    // Spec 017: Settings opens to guests with exactly one tab (Feedback); the admin keeps Users and
    // AI Models and gains the Feedback inbox. Each tab re-guards itself below.
    path: 'settings',
    canActivate: [authGuard, roleGuard('admin', 'guest')],
    children: [
      { path: '', pathMatch: 'full', component: SettingsHomeComponent },
      ...settingsAdminRoutes,
      {
        path: 'feedback',
        canActivate: [roleGuard('guest')],
        loadChildren: () =>
          import('@jordylab-fe/feedback/ui').then((m) => m.guestFeedbackRoutes),
      },
      {
        path: 'inbox',
        canActivate: [roleGuard('admin')],
        loadChildren: () =>
          import('@jordylab-fe/feedback/ui').then((m) => m.adminInboxRoutes),
      },
    ],
  },
  { path: '', redirectTo: 'fna', pathMatch: 'full' },
];
