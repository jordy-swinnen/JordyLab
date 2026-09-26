import { Injector } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { AUTH_CONFIG, AuthService } from '@jordylab-fe/shared/auth';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { environment } from './environments/environment';

const injector = Injector.create({
  providers: [{ provide: AUTH_CONFIG, useValue: environment }, AuthService],
});
const auth = injector.get(AuthService);

auth
  .init()
  .then(() => bootstrapApplication(App, appConfig))
  .catch((err) => console.error(err));
