import { switchToWebView } from '../support/contexts';
import { requiredEnvironment } from '../support/environment';
import { signInNatively } from '../support/native-login';

const environment = requiredEnvironment();

describe('Native Keycloak login in the installed app', () => {
  it('signs in through the Custom Tab, returns through the App Link and shows the signed-in app', async () => {
    await signInNatively();

    await switchToWebView(environment.androidPackage);
    await expect($('[data-testid="user-menu-trigger"]')).toBeDisplayed();
    await expect($('//button[contains(., "Sign in with Keycloak")]')).not.toBeDisplayed();
  });
});
