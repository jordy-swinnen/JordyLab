import { Injector } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { AUTH_CONFIG, AuthService } from '@jordylab-fe/shared/auth';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { environment } from './environments/environment';

// A throwaway injector just to resolve AuthService's AUTH_CONFIG dependency for this early,
// pre-bootstrap Keycloak check — this instance is separate from the one components later
// inject via `providedIn: 'root'`, matching the pre-existing pre-bootstrap warm-up pattern.
const injector = Injector.create({
  providers: [{ provide: AUTH_CONFIG, useValue: environment }, AuthService],
});
const auth = injector.get(AuthService);

auth
  .init()
  .then(() => bootstrapApplication(App, appConfig))
  .catch((err) => console.error(err));
