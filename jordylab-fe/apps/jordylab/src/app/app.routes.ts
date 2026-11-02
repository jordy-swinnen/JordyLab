import { Route } from '@angular/router';
import { authGuard, LoginComponent, roleGuard } from '@jordylab-fe/shared/auth';
import { ShareLandingComponent } from '@jordylab-fe/shared/platform/ui';
import { AppSettingsPageComponent } from './app-settings/app-settings-page.component';
import { AwaitingApprovalComponent } from './awaiting-approval/awaiting-approval.component';
import { legacyCallbackRedirect, legacyScreenRedirect } from './legacy-mobile-redirect';
import { NoAppsComponent } from './no-apps/no-apps.component';

export const appRoutes: Route[] = [
  { path: 'login', component: LoginComponent },
  {
    path: 'awaiting-approval',
    canActivate: [authGuard],
    component: AwaitingApprovalComponent,
  },
  {
    // Spec 014: approved, but the admin has not granted any app yet. A pending account must still
    // see "awaiting approval", never the "approved, nothing shared" wording.
    path: 'no-apps',
    canActivate: [authGuard, roleGuard('admin', 'guest')],
    component: NoAppsComponent,
  },
  {
    // The Web Share Target destination (spec 018 US2): the OS share sheet of the installed app lands here with
    // ?title&text&url. authGuard (not roleGuard) so a signed-out share runs login first and returns here with the
    // payload intact; the landing screen itself role-filters its destinations.
    path: 'share',
    canActivate: [authGuard],
    component: ShareLandingComponent,
  },
  // Legacy links from the retired native app (research D7): old notification taps and the native login callback.
  { path: 'mobile/open', canActivate: [legacyScreenRedirect], children: [] },
  { path: 'mobile/callback', canActivate: [legacyCallbackRedirect], children: [] },
  {
    path: 'fna',
    canActivate: [authGuard, roleGuard('admin')],
    loadChildren: () => import('@jordylab-fe/fna/ui').then((m) => m.fnaRoutes),
  },
  {
    // Spec 014: the Game Catalog needs the per-app grant, not just approval.
    path: 'games',
    canActivate: [authGuard, roleGuard('admin', 'app-gamecatalog')],
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
    path: 'settings',
    canActivate: [authGuard, roleGuard('admin')],
    loadChildren: () =>
      import('@jordylab-fe/settings/ui').then((m) => m.settingsRoutes),
  },
  { path: '', redirectTo: 'fna', pathMatch: 'full' },
];
