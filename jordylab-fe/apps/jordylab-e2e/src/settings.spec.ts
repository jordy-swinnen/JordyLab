import { environment, expect, test } from './support/fixtures';
import { pointAppAtKeycloak } from './support/session';

// A guest signs up through Keycloak's own registration page (the UI, no direct writes), lands on the awaiting-approval screen,
// and the admin approves them in Settings → Users.
test.describe('Admin Settings: approving a new user', () => {
  test('a pending sign-up appears in Users and moves to Approved', async ({ browser, page }) => {
    const suffix = Date.now().toString(36);
    const email = `guest-${suffix}@example.test`;

    const guestContext = await browser.newContext({ baseURL: environment.baseUrl, storageState: undefined });
    await pointAppAtKeycloak(guestContext, environment.keycloakUrl);
    const guestPage = await guestContext.newPage();
    await guestPage.goto('/');
    await guestPage.getByRole('button', { name: 'Sign in with Keycloak' }).click();
    await guestPage.getByRole('link', { name: /register/i }).click();
    // Keycloak's registration page (third-party DOM): its documented element ids are the stable handle.
    await guestPage.locator('#firstName').fill('Guest');
    await guestPage.locator('#lastName').fill(suffix);
    await guestPage.locator('#email').fill(email);
    const username = guestPage.locator('#username');
    if (await username.count()) {
      await username.fill(`guest-${suffix}`);
    }
    await guestPage.locator('#password').fill(`E2e-${suffix}-Pass1!`);
    await guestPage.locator('#password-confirm').fill(`E2e-${suffix}-Pass1!`);
    await guestPage.locator('input[type=submit], button[type=submit]').click();
    await expect(guestPage.getByRole('heading', { level: 1, name: "You're almost in" })).toBeVisible();
    await guestContext.close();

    await page.goto('/settings/users');
    await expect(page.getByRole('heading', { level: 2, name: 'Users' })).toBeVisible();
    const pendingRow = page.locator('div.panel', { hasText: email });
    await expect(pendingRow).toBeVisible();

    await pendingRow.getByTestId('approve-user').click();

    await expect(pendingRow.getByTestId('revoke-user')).toBeVisible();
  });
});
