import type { Locator, Page } from '@playwright/test';

/**
 * Signs in through the app's own form (spec 019 US1). Leaves the page on the signed-in app; every caller checks that with
 * a navigation or account-menu assertion so a failed sign-in fails where it happened.
 */
export async function signIn(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('login-username').fill(username);
  await page.getByTestId('login-password').fill(password);
  await page.getByTestId('login-submit').click();
  await page.getByRole('navigation', { name: 'Primary' }).waitFor();
}

/** Signs the current page out through the account menu. */
export async function signOut(page: Page): Promise<void> {
  await visibleUserMenu(page).click();
  await page.getByTestId('user-menu-sign-out').click();
  await page.getByTestId('login-submit').waitFor();
}

/** The app renders the account menu once per layout (wide and narrow); only one of them is visible at a given width. */
export function visibleUserMenu(page: Page): Locator {
  return page.getByTestId('user-menu-trigger').filter({ visible: true });
}
