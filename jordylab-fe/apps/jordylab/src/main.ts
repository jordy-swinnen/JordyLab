import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { AuthService } from './app/auth/auth.service';
import { initFederation } from '@softarc/native-federation-runtime';

initFederation({
  fna: 'http://localhost:4300/remoteEntry.json',
  gamecatalog: 'http://localhost:4400/remoteEntry.json',
})
  .then(() => {
    const auth = new AuthService();
    return auth.init().then(() => bootstrapApplication(App, appConfig));
  })
  .catch((err) => console.error(err));
