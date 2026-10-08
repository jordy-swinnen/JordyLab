import type { Browser, BrowserContext, Page } from '@playwright/test';
import { expect } from '@playwright/test';
import type { E2eEnvironment } from './environment';
import { pointAppAtKeycloak, signInThroughKeycloak } from './session';

export interface SignedInGuest {
  readonly context: BrowserContext;
  readonly page: Page;
  readonly email: string;
}

/**
 * A second person, through the app only: they register on Keycloak's page, the admin approves them in Settings → Users, and they
 * sign in again so their token carries the guest role. Returns their own browser context, already on the signed-in app.
 */
export async function signedInApprovedGuest(
  browser: Browser,
  adminPage: Page,
  environment: E2eEnvironment,
): Promise<SignedInGuest> {
  const suffix = Date.now().toString(36);
  const email = `voter-${suffix}@example.test`;
  const password = `E2e-${suffix}-Pass1!`;

  const registering = await browser.newContext({ baseURL: environment.baseUrl, storageState: undefined });
  await pointAppAtKeycloak(registering, environment.keycloakUrl);
  const registrationPage = await registering.newPage();
  await registrationPage.goto('/');
  await registrationPage.getByRole('button', { name: 'Sign in with Keycloak' }).click();
  await registrationPage.getByRole('link', { name: /register/i }).click();
  // Keycloak's registration page is third-party DOM: its documented element ids are the stable handle.
  await registrationPage.locator('#firstName').fill('Voter');
  await registrationPage.locator('#lastName').fill(suffix);
  await registrationPage.locator('#email').fill(email);
  const username = registrationPage.locator('#username');
  if (await username.count()) {
    await username.fill(`voter-${suffix}`);
  }
  await registrationPage.locator('#password').fill(password);
  await registrationPage.locator('#password-confirm').fill(password);
  await registrationPage.locator('input[type=submit], button[type=submit]').click();
  await expect(registrationPage.getByRole('heading', { level: 1, name: "You're almost in" })).toBeVisible();
  await registering.close();

  await adminPage.goto('/settings/users');
  const pendingRow = adminPage.locator('div.panel', { hasText: email });
  await pendingRow.getByTestId('approve-user').click();
  await expect(pendingRow.getByTestId('revoke-user')).toBeVisible();

  const context = await browser.newContext({ baseURL: environment.baseUrl, storageState: undefined });
  await pointAppAtKeycloak(context, environment.keycloakUrl);
  const page = await context.newPage();
  await signInThroughKeycloak(page, email, password);

  return { context, page, email };
}
