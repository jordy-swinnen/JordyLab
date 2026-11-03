import { environment, expect, test } from './support/fixtures';
import { signIn, signOut, visibleUserMenu } from './support/session';

test.describe('Sign-in and session reuse', () => {
  test('the saved admin session opens the app without another login', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('navigation', { name: 'Primary' })).toBeVisible();
    await expect(visibleUserMenu(page)).toBeVisible();
    await expect(page.getByTestId('login-submit')).toHaveCount(0);
  });

  test('a visitor without a session sees the login page, signs in and signs out again', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: environment.baseUrl, storageState: undefined });
    const page = await context.newPage();

    await page.goto('/');
    await expect(page.getByTestId('login-submit')).toBeVisible();

    await signIn(page, environment.adminUsername, environment.adminPassword);
    await expect(visibleUserMenu(page)).toBeVisible();
    await expect(page.getByTestId('login-submit')).toHaveCount(0);

    await signOut(page);
    await expect(page.getByTestId('login-submit')).toBeVisible();

    await context.close();
  });

  test('a wrong password is refused with a clear message', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: environment.baseUrl, storageState: undefined });
    const page = await context.newPage();

    await page.goto('/login');
    await page.getByTestId('login-username').fill(environment.adminUsername);
    await page.getByTestId('login-password').fill('definitely-not-the-password');
    await page.getByTestId('login-submit').click();

    await expect(page.getByTestId('login-error')).toHaveText('Invalid username or password.');
    await expect(page.getByTestId('login-submit')).toBeVisible();

    await context.close();
  });

  test('the forgot-password link opens the admin-approved reset request', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: environment.baseUrl, storageState: undefined });
    const page = await context.newPage();

    await page.goto('/login');
    await page.getByTestId('forgot-password-link').click();

    await expect(page).toHaveURL(/\/forgot-password/);
    await expect(page.getByTestId('reset-username')).toBeVisible();
    await expect(page.getByTestId('reset-password')).toBeVisible();
    await expect(page.getByTestId('reset-submit')).toHaveText('Send reset request');

    await context.close();
  });
});
