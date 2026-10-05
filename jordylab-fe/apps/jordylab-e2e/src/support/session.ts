import type { BrowserContext, Locator, Page } from '@playwright/test';

/**
 * environment.e2e.ts reads the Keycloak address from `window.__JORDYLAB_E2E__` because every run's Keycloak is on a random
 * free port. The init script must run before the bundle executes, so it is added to the context, not the page.
 */
export async function pointAppAtKeycloak(context: BrowserContext, keycloakUrl: string): Promise<void> {
  await context.addInitScript((url: string) => {
    (globalThis as { __JORDYLAB_E2E__?: { keycloakUrl: string } }).__JORDYLAB_E2E__ = { keycloakUrl: url };
  }, keycloakUrl);
}

/** Signs in through the real Keycloak login page. Leaves the page on the signed-in app. */
export async function signInThroughKeycloak(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in with Keycloak' }).click();
  // Keycloak's own pages are third-party DOM without accessible labels; its documented element ids are the stable handle here.
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await page.getByRole('navigation', { name: 'Primary' }).waitFor();
}

/** The app renders the account menu once per layout (wide and narrow); only one of them is visible at a given width. */
export function visibleUserMenu(page: Page): Locator {
  return page.getByTestId('user-menu-trigger').filter({ visible: true });
}
