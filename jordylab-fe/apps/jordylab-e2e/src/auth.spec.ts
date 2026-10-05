import { environment, expect, test } from './support/fixtures';
import { pointAppAtKeycloak, signInThroughKeycloak, visibleUserMenu } from './support/session';

test.describe('Sign-in and session reuse', () => {
  test('the saved admin session opens the app without another login', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('navigation', { name: 'Primary' })).toBeVisible();
    await expect(visibleUserMenu(page)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign in with Keycloak' })).toHaveCount(0);
  });

  test('a visitor without a session sees the login page, signs in and signs out again', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: environment.baseUrl, storageState: undefined });
    const page = await context.newPage();
    // Same init script the fixture adds, because this context is created by hand.
    await pointAppAtKeycloak(context, environment.keycloakUrl);

    await page.goto('/');
    await expect(page.getByRole('button', { name: 'Sign in with Keycloak' })).toBeVisible();

    await signInThroughKeycloak(page, environment.adminUsername, environment.adminPassword);
    await expect(visibleUserMenu(page)).toBeVisible();

    await visibleUserMenu(page).click();
    await page.getByTestId('user-menu-sign-out').click();
    await expect(page.getByRole('button', { name: 'Sign in with Keycloak' })).toBeVisible();

    await context.close();
  });
});
