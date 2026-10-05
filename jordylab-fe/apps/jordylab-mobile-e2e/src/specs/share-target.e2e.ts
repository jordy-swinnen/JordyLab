import { execFileSync } from 'node:child_process';
import { switchToWebView } from '../support/contexts';
import { requiredEnvironment } from '../support/environment';
import { signInNatively } from '../support/native-login';

const environment = requiredEnvironment();

describe('Share target', () => {
  it('opens the share landing with the shared text when another app shares to JordyLab', async () => {
    await signInNatively();

    // What the Android share sheet does: an ACTION_SEND intent with text/plain, addressed to the app.
    execFileSync('adb', [
      'shell', 'am', 'start',
      '-a', 'android.intent.action.SEND',
      '-t', 'text/plain',
      '--es', 'android.intent.extra.TEXT', 'https://example.test/a-shared-game',
      '-n', `${environment.androidPackage}/.MainActivity`,
    ]);

    await switchToWebView(environment.androidPackage);
    const heading = await $('//h2[contains(., "Share to JordyLab")]');
    await heading.waitForDisplayed({ timeout: 30_000, timeoutMsg: 'The share landing did not open' });
    await expect($('body')).toHaveText(expect.stringContaining('https://example.test/a-shared-game'));
  });
});
