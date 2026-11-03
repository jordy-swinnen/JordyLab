import { environment, expect, test } from './support/fixtures';
import { signIn } from './support/session';

/**
 * Spec 014 parity through the new identity module (spec 019 US4/US6): approve a registration, grant and revoke the Game
 * Catalog, see and end the person's session, and delete the account — all through Settings → Users.
 */
test.describe('User management', () => {
  test('approve, grant, revoke, end sessions and delete a person', async ({ browser, page }) => {
    const suffix = Date.now().toString(36);
    const username = `managed-${suffix}`;
    const displayName = `Managed ${suffix}`;
    const password = `E2e-${suffix}-Pass1!`;

    const registering = await browser.newContext({
      baseURL: environment.baseUrl,
      storageState: undefined,
    });
    const registrationPage = await registering.newPage();
    await registrationPage.goto('/register');
    await registrationPage.getByTestId('register-username').fill(username);
    await registrationPage.getByTestId('register-display-name').fill(displayName);
    await registrationPage.getByTestId('register-password').fill(password);
    await registrationPage.getByTestId('register-submit').click();
    await expect(registrationPage).toHaveURL(/\/awaiting-approval/);
    await registering.close();

    await page.goto('/settings/users');
    const row = page.locator('div.panel', { hasText: username });
    await expect(row).toBeVisible();
    await row.getByTestId('approve-user').click();
    await expect(row.getByTestId('revoke-user')).toBeVisible();

    // Approval grants Game Catalog by default; the toggle revokes and restores it.
    await expect(row.getByTestId('revoke-app')).toBeVisible();
    await row.getByTestId('revoke-app').click();
    await expect(row.getByTestId('grant-app')).toBeVisible();
    await row.getByTestId('grant-app').click();
    await expect(row.getByTestId('revoke-app')).toBeVisible();

    // The person signs in (one real session), then the admin ends it.
    const guestContext = await browser.newContext({
      baseURL: environment.baseUrl,
      storageState: undefined,
    });
    const guestPage = await guestContext.newPage();
    await signIn(guestPage, username, password);

    await page.goto('/settings/users');
    const managedRow = page.locator('div.panel', { hasText: username });
    await managedRow.getByTestId('toggle-sessions').click();
    const sessions = managedRow.getByTestId('sessions-panel');
    // The registration auto-login already created one session; the approved sign-in adds another.
    await expect(sessions.getByTestId('end-session').first()).toBeVisible();
    await sessions.getByTestId('end-all-sessions').click();
    await expect(sessions).toContainText('No active sessions.');

    // Deleting ends the session immediately and removes the account.
    await managedRow.getByTestId('delete-user').click();
    await expect(managedRow.getByTestId('delete-user')).toHaveText('Confirm delete');
    await managedRow.getByTestId('delete-user').click();
    await expect(page.locator('div.panel', { hasText: username })).toHaveCount(0);

    await guestPage.reload();
    await expect(guestPage.getByTestId('login-username')).toBeVisible();
    await guestContext.close();
  });
});
