import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

/**
 * Registers the installability-only service worker (spec 018 FR-002). Chrome's automatic install prompt still requires a
 * registered fetch handler, even though a menu install no longer needs one. Failure is logged and never blocks the app.
 */
function registerServiceWorker(): void {
  if (!('serviceWorker' in navigator)) {
    return;
  }
  navigator.serviceWorker.register('/sw.js').catch((error: unknown) => {
    console.error('Service worker registration failed', error);
  });
}

bootstrapApplication(App, appConfig)
  .then(registerServiceWorker)
  .catch((err) => console.error(err));
