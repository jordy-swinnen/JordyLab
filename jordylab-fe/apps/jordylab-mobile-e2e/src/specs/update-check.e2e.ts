import { readFile } from 'node:fs/promises';
import { switchToWebView } from '../support/contexts';
import { requiredEnvironment } from '../support/environment';
import { signInNatively } from '../support/native-login';

const environment = requiredEnvironment();

async function publishNewerRelease(): Promise<void> {
  const tokenResponse = await fetch(`${environment.keycloakUrl}/realms/jordylab/protocol/openid-connect/token`, {
    method: 'POST',
    body: new URLSearchParams({ grant_type: 'client_credentials', client_id: 'e2e-ingest', client_secret: environment.ingestClientSecret }),
  });
  if (!tokenResponse.ok) {
    throw new Error(`Could not get the publisher token (HTTP ${tokenResponse.status})`);
  }
  const { access_token: accessToken } = (await tokenResponse.json()) as { access_token: string };

  // The app's own publish endpoint, with a second debug APK (higher versionCode, same debug signing certificate the backend pins).
  const form = new FormData();
  form.set('versionName', '0.0.2-e2e');
  form.set('versionCode', '2');
  form.set('releaseNotes', 'End-to-end test release');
  // Without this the first release's minimum supported version is its own (2): the installed app (1) would be blocked by the "update required"
  // screen. With 1 it is an ordinary "update available".
  form.set('minSupportedVersionCode', '1');
  form.set('file', new Blob([new Uint8Array(await readFile(environment.apkNewer))], { type: 'application/vnd.android.package-archive' }), 'jordylab-0.0.2-e2e.apk');
  const response = await fetch(`${environment.apiOrigin}/api/mobile/releases`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}` },
    body: form,
  });
  if (!response.ok) {
    const reason = await response.text();
    // A retried test publishes again: the release from the first attempt is already there, which is what the test needs.
    if (response.status === 400 && reason.includes('VERSION_CODE_NOT_MONOTONIC')) {
      return;
    }
    throw new Error(`Publishing the newer release was rejected (HTTP ${response.status}): ${reason}`);
  }
}

describe('Update check', () => {
  it('shows "Update available" when a newer release was published', async () => {
    await publishNewerRelease();
    await signInNatively();

    // The check runs on start and on resume; the first run happened signed out, so trigger the resume one: background, then foreground.
    await driver.execute('mobile: backgroundApp', { seconds: 2 });
    await switchToWebView(environment.androidPackage);

    const banner = await $('[role="status"]');
    await banner.waitForDisplayed({ timeout: 45_000, timeoutMsg: 'No update banner after publishing a newer release' });
    await expect(banner).toHaveText(expect.stringContaining('Update available: v0.0.2-e2e'));
  });
});
