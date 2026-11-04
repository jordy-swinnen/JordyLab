import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { ThemeStore } from '@jordylab-fe/shared/theme/api';
import { appRoutes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Starts the theme store so the browser's address-bar colour follows the stored theme from the first screen on.
    provideAppInitializer(() => void inject(ThemeStore)),
    provideRouter(appRoutes),
    provideHttpClient(withXhr()),
  ],
};
