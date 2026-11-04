import type { BrowserContext, Page } from '@playwright/test';
import { environment, expect, test } from './support/fixtures';
import { signIn, signOut } from './support/session';

/**
 * Passkeys end to end (spec 019 US2/US7) with Chrome's CDP virtual authenticator: no fingerprint hardware is needed, but the
 * browser runs the real WebAuthn ceremonies against the backend. Covers enrollment in Account settings, usernameless sign-in,
 * removal, and the password fallback afterwards.
 */
async function addVirtualAuthenticator(context: BrowserContext, page: Page) {
  const client = await context.newCDPSession(page);
  await client.send('WebAuthn.enable');
  const { authenticatorId } = await client.send('WebAuthn.addVirtualAuthenticator', {
    options: {
      protocol: 'ctap2',
      transport: 'internal',
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true,
    },
  });

  return { client, authenticatorId };
}

test.describe('Passkeys', () => {
  test('enroll, sign in with the passkey, remove it, and fall back to the password', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: environment.baseUrl, storageState: undefined });
    const page = await context.newPage();
    try {
      await signIn(page, environment.adminUsername, environment.adminPassword);
      await addVirtualAuthenticator(context, page);

      await page.goto('/account');
      await expect(page.getByTestId('account-passkeys-empty')).toBeVisible();
      await page.getByTestId('account-passkey-label').fill('E2E virtual authenticator');
      await page.getByTestId('account-passkey-add').click();
      await expect(page.getByTestId('account-passkey-remove')).toBeVisible();
      await expect(page.getByText('E2E virtual authenticator')).toBeVisible();

      await signOut(page);
      await page.getByTestId('login-passkey').click();
      await expect(page.getByRole('navigation', { name: 'Primary' })).toBeVisible();
      await expect(page.getByTestId('login-submit')).toHaveCount(0);

      // Remove the passkey, then prove the password still signs in.
      await page.goto('/account');
      await page.getByTestId('account-passkey-remove').click();
      await expect(page.getByTestId('account-passkeys-empty')).toBeVisible();

      await signOut(page);
      await page.getByTestId('login-username').fill(environment.adminUsername);
      await page.getByTestId('login-password').fill(environment.adminPassword);
      await page.getByTestId('login-submit').click();
      await expect(page.getByRole('navigation', { name: 'Primary' })).toBeVisible();
    } finally {
      await context.close();
    }
  });
});
