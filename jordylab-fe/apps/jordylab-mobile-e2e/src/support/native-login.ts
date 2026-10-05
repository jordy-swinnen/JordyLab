import { requiredEnvironment } from './environment';
import { switchToNative, switchToWebView } from './contexts';

const environment = requiredEnvironment();
const CHROME_PACKAGE = 'com.android.chrome';
const STEP_TIMEOUT_MS = 45_000;

/**
 * Native Keycloak login as a user would do it: tap sign in in the WebView, sign in on Keycloak's page in the Chrome Custom Tab, and
 * come back into the app through the App Link. Ends in the WebView context on the signed-in app.
 */
export async function signInNatively(): Promise<void> {
  await switchToWebView(environment.androidPackage);
  const signInButton = await $('//button[contains(., "Sign in with Keycloak")]');
  await signInButton.waitForDisplayed({ timeout: STEP_TIMEOUT_MS });
  await signInButton.click();

  await switchToNative();
  await driver.waitUntil(async () => (await driver.getCurrentPackage()) === CHROME_PACKAGE, {
    timeout: STEP_TIMEOUT_MS,
    timeoutMsg: 'The Chrome Custom Tab did not open after tapping sign in',
  });

  // Keycloak's page inside Chrome: web content is exposed to UiAutomator as accessibility nodes, so fields are found by class and order.
  const username = await $('android=new UiSelector().className("android.widget.EditText").instance(0)');
  await username.waitForDisplayed({ timeout: STEP_TIMEOUT_MS });
  await username.setValue(environment.adminUsername);
  const password = await $('android=new UiSelector().className("android.widget.EditText").instance(1)');
  await password.setValue(environment.adminPassword);
  try {
    await driver.hideKeyboard();
  } catch {
    // No keyboard to hide: fine.
  }
  await (await $('android=new UiSelector().text("Sign In")')).click();

  await driver.waitUntil(async () => (await driver.getCurrentPackage()) === environment.androidPackage, {
    timeout: STEP_TIMEOUT_MS,
    timeoutMsg: 'The app did not come back from the Custom Tab (the App Link callback was not handled)',
  });
  await switchToWebView(environment.androidPackage);
  await $('[data-testid="user-menu-trigger"]').waitForDisplayed({
    timeout: STEP_TIMEOUT_MS,
    timeoutMsg: 'The app is back but not signed in (no account menu)',
  });
}
