import { execFileSync } from 'node:child_process';
import { switchToNative, switchToWebView } from '../support/contexts';
import { requiredEnvironment } from '../support/environment';
import { signInNatively } from '../support/native-login';

const environment = requiredEnvironment();
const SHARED_LINK = 'https://example.test/a-shared-game';

/** What the Android share sheet does: an ACTION_SEND intent with text/plain, addressed to the app. */
function shareToApp(text: string): void {
  execFileSync('adb', [
    'shell',
    'am',
    'start',
    '-a',
    'android.intent.action.SEND',
    '-t',
    'text/plain',
    '--es',
    'android.intent.extra.TEXT',
    text,
    '-n',
    `${environment.androidPackage}/.MainActivity`,
  ]);
}

async function expectShareLanding(text: string): Promise<void> {
  await switchToWebView(environment.androidPackage);
  const heading = await $('//h2[contains(., "Share to JordyLab")]');
  await heading.waitForDisplayed({
    timeout: 30_000,
    timeoutMsg: 'The share landing did not open',
  });
  await expect($('body')).toHaveText(expect.stringContaining(text));
}

describe('Share target', () => {
  it('opens the share landing with the shared text when another app shares to JordyLab', async () => {
    await signInNatively();

    shareToApp(SHARED_LINK);

    await expectShareLanding(SHARED_LINK);
  });

  // BUG-062: shared from the share sheet while the app was not running, the app opened and nothing happened (and a share
  // that needed a login was lost once the login finished). Signed out is what a restart gives without fingerprint unlock.
  it('starts the app cold from a share, asks for the login first and then shows the shared text', async () => {
    await switchToNative();
    execFileSync('adb', [
      'shell',
      'am',
      'force-stop',
      environment.androidPackage,
    ]);

    shareToApp(SHARED_LINK);

    await switchToWebView(environment.androidPackage);
    const signInButton = await $(
      '//button[contains(., "Sign in with Keycloak")]',
    );
    await signInButton.waitForDisplayed({
      timeout: 45_000,
      timeoutMsg:
        'A cold share while signed out did not lead to the login page',
    });

    await signInNatively();

    await expectShareLanding(SHARED_LINK);
  });
});
