import { requiredEnvironment } from '../support/environment';

const environment = requiredEnvironment();

// Android Chrome is detected as `web-android`, so a signed-in visitor gets the APK install dialog (30 days of silence after "Not now").
describe('Install prompt in Chrome on Android', () => {
  it('offers the app to a signed-in visitor and stays away after "Not now"', async () => {
    await browser.url(environment.webUrl);
    await $('//button[contains(., "Sign in with Keycloak")]').click();
    await $('#username').setValue(environment.adminUsername);
    await $('#password').setValue(environment.adminPassword);
    // Enter submits the form; the on-screen keyboard of this small screen can cover the Sign In button.
    await browser.keys('Enter');

    const dialog = await $('[aria-label="Get the JordyLab app"]');
    await dialog.waitForDisplayed({ timeout: 45_000, timeoutMsg: 'No install dialog after signing in on Android Chrome' });
    await expect(dialog).toHaveText(expect.stringContaining('Get the JordyLab app'));

    await $('//button[contains(., "Not now")]').click();
    await expect(dialog).not.toBeDisplayed();
    await browser.refresh();
    await $('[data-testid="user-menu-trigger"]').waitForDisplayed({ timeout: 45_000 });
    await expect($('[aria-label="Get the JordyLab app"]')).not.toBeDisplayed();
  });
});
