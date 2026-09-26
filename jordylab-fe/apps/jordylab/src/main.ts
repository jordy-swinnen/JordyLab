import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { AuthService } from './app/auth/auth.service';

const auth = new AuthService();

auth
  .init()
  .then(() => bootstrapApplication(App, appConfig))
  .catch((err) => console.error(err));
