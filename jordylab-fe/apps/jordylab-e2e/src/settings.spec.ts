import { environment, expect, test } from './support/fixtures';

// A guest signs up on the app's own registration form (the UI, no direct writes), lands on the awaiting-approval screen,
// and the admin approves them in Settings → Users.
test.describe('Admin Settings: approving a new user', () => {
  test('a pending sign-up appears in Users and moves to Approved', async ({ browser, page }) => {
    const suffix = Date.now().toString(36);
    const username = `guest-${suffix}`;
    const displayName = `Guest ${suffix}`;
    const password = `E2e-${suffix}-Pass1!`;

    const guestContext = await browser.newContext({
      baseURL: environment.baseUrl,
      storageState: undefined,
    });
    const guestPage = await guestContext.newPage();
    await guestPage.goto('/register');
    await guestPage.getByTestId('register-username').fill(username);
    await guestPage.getByTestId('register-display-name').fill(displayName);
    await guestPage.getByTestId('register-password').fill(password);
    await guestPage.getByTestId('register-submit').click();
    await expect(guestPage).toHaveURL(/\/awaiting-approval/);
    await guestContext.close();

    await page.goto('/settings/users');
    await expect(page.getByRole('heading', { level: 2, name: 'Users' })).toBeVisible();
    const row = page.locator('div.panel', { hasText: username });
    await expect(row).toBeVisible();
    await expect(row).toContainText(displayName);

    await row.getByTestId('approve-user').click();

    await expect(row.getByTestId('revoke-user')).toBeVisible();
    await expect(row.getByTestId('grant-app').or(row.getByTestId('revoke-app'))).toBeVisible();
  });
});
