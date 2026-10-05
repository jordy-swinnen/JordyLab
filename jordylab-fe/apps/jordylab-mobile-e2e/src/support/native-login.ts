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
  try {
    await $('[data-testid="user-menu-trigger"]').waitForDisplayed({ timeout: STEP_TIMEOUT_MS });
  } catch {
    throw new Error(`The app is back but not signed in (no account menu). From inside the WebView: ${await probeFromWebView()}`);
  }
}

/** When the app is back but signed out, ask the WebView itself what it can reach: the answer separates a network block from a CORS block. */
async function probeFromWebView(): Promise<string> {
  const probe = await driver.executeAsync(
    (keycloakUrl: string, apiOrigin: string, done: (result: string) => void) => {
      const attempt = async (label: string, url: string, init: RequestInit): Promise<string> => {
        try {
          const response = await fetch(url, init);
          return `${label}: HTTP ${response.status} ${init.mode ?? 'cors'}`;
        } catch (error) {
          return `${label}: ${(error as Error).message} (${init.mode ?? 'cors'})`;
        }
      };
      void Promise.all([
        attempt('keycloak discovery', `${keycloakUrl}/realms/jordylab/.well-known/openid-configuration`, { mode: 'cors' }),
        attempt('keycloak discovery', `${keycloakUrl}/realms/jordylab/.well-known/openid-configuration`, { mode: 'no-cors' }),
        attempt('keycloak token', `${keycloakUrl}/realms/jordylab/protocol/openid-connect/token`, {
          method: 'POST',
          mode: 'cors',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: 'grant_type=authorization_code&client_id=jordylab-mobile&code=x',
        }),
        attempt('backend health', `${apiOrigin}/actuator/health`, { mode: 'cors' }),
        attempt('backend health', `${apiOrigin}/actuator/health`, { mode: 'no-cors' }),
      ]).then((results) => done(`origin ${location.origin}; ${results.join('; ')}`));
    },
    environment.keycloakUrl,
    environment.apiOrigin,
  );

  return String(probe);
}
