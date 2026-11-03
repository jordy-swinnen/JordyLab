import type { Browser, BrowserContext, Page } from '@playwright/test';
import { expect } from '@playwright/test';
import type { E2eEnvironment } from './environment';
import { signIn } from './session';

export interface SignedInGuest {
  readonly context: BrowserContext;
  readonly page: Page;
  readonly username: string;
  readonly displayName: string;
}

/**
 * A second person through the app only (spec 019 US6 parity): they register on `/register`, land on the awaiting-approval
 * screen, the admin approves them in Settings → Users, and they sign in with the password they chose. No email exists.
 * Returns their own browser context, already on the signed-in app.
 */
export async function signedInApprovedGuest(
  browser: Browser,
  adminPage: Page,
  environment: E2eEnvironment,
): Promise<SignedInGuest> {
  const suffix = Date.now().toString(36);
  const username = `voter-${suffix}`;
  const displayName = `Voter ${suffix}`;
  const password = `E2e-${suffix}-Pass1!`;

  const registering = await browser.newContext({ baseURL: environment.baseUrl, storageState: undefined });
  const registrationPage = await registering.newPage();
  await registrationPage.goto('/register');
  await registrationPage.getByTestId('register-username').fill(username);
  await registrationPage.getByTestId('register-display-name').fill(displayName);
  await registrationPage.getByTestId('register-password').fill(password);
  await registrationPage.getByTestId('register-submit').click();
  await expect(registrationPage).toHaveURL(/\/awaiting-approval/);
  await registering.close();

  await adminPage.goto('/settings/users');
  const pendingRow = adminPage.locator('div.panel', { hasText: username });
  await expect(pendingRow).toBeVisible();
  await pendingRow.getByTestId('approve-user').click();
  await expect(pendingRow.getByTestId('revoke-user')).toBeVisible();

  const context = await browser.newContext({ baseURL: environment.baseUrl, storageState: undefined });
  const page = await context.newPage();
  await signIn(page, username, password);

  return { context, page, username, displayName };
}
