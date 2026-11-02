import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

/**
 * `screen` → in-app route, mirroring the retired native `AppLinkService`'s table (spec 018 research D7). ntfy
 * notifications delivered before the PWA cutover still carry `https://{domain}/mobile/open?screen=…` links and live on
 * the admin's phone for weeks, so they must land on the right screen instead of an empty outlet.
 */
const SCREEN_ROUTES: Readonly<Record<string, string>> = {
  'settings-users': '/settings/users',
  'fna-briefing': '/fna/briefing',
};

/** Redirects a legacy `/mobile/open?screen=…` notification tap to its web route (unknown screens land on the start page). */
export const legacyScreenRedirect: CanActivateFn = (route) => {
  const screen = route.queryParamMap.get('screen');
  const target = screen ? SCREEN_ROUTES[screen] : undefined;

  return inject(Router).parseUrl(target ?? '/');
};

/** The native app's login callback has nothing to complete any more — the standard login page owns sessions. */
export const legacyCallbackRedirect: CanActivateFn = () => inject(Router).parseUrl('/login');
