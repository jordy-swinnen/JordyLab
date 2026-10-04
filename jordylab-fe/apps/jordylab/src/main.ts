import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { createPreBootstrapAuth } from './app/pre-bootstrap-auth';
import { environment } from './environments/environment';

createPreBootstrapAuth(environment)
  .init()
  .then(() => bootstrapApplication(App, appConfig))
  .catch((err) => console.error(err));
