import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { AUTH_CONFIG, authInterceptor } from '@jordylab-fe/shared/auth';
import { API_BASE_URL, apiBaseUrlInterceptor } from '@jordylab-fe/shared/platform/api';
import { appRoutes } from './app.routes';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(appRoutes),
    // apiBaseUrlInterceptor MUST run after authInterceptor — order here is the registration
    // order (research D5, contracts/app-shell-contract.md).
    provideHttpClient(withInterceptors([authInterceptor, apiBaseUrlInterceptor])),
    { provide: AUTH_CONFIG, useValue: environment },
    { provide: API_BASE_URL, useValue: (environment as { apiBaseUrl?: string }).apiBaseUrl ?? '' },
  ],
};
