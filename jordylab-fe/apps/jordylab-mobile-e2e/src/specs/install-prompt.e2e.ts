import { switchToWebView } from '../support/contexts';
import { requiredEnvironment } from '../support/environment';
import { signInNatively } from '../support/native-login';

const environment = requiredEnvironment();

// The install prompt exists for browser visitors on Android only ("never inside the native app"). Its positive case is in src/browser;
// this is the other half, inside the app: a signed-in user is never offered to install the app they are already running.
describe('Install prompt inside the installed app', () => {
  it('is not offered to a signed-in user', async () => {
    await signInNatively();
    await switchToWebView(environment.androidPackage);
    // Give the prompt its chance to appear (it renders as soon as the role is known).
    await browser.pause(3_000);

    await expect($('[aria-label="Get the JordyLab app"]')).not.toBeExisting();
    await expect($('[aria-label="Add JordyLab to Home Screen"]')).not.toBeExisting();
  });
});
