import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { AUTH_CONFIG, authInterceptor } from '@jordylab-fe/shared/auth';
import { ThemeStore } from '@jordylab-fe/shared/theme/api';
import { appRoutes } from './app.routes';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Starts the theme store so the browser's address-bar colour follows the stored theme from the first screen on.
    provideAppInitializer(() => void inject(ThemeStore)),
    provideRouter(appRoutes),
    provideHttpClient(withXhr(), withInterceptors([authInterceptor])),
    { provide: AUTH_CONFIG, useValue: environment },
  ],
};
