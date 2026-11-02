import type { Page } from '@playwright/test';
import { environment, expect, test } from './support/fixtures';
import { signedInApprovedGuest } from './support/guest';

// The theme is chosen per device in Settings → App (reachable by an administrator and a guest, on the web) and remembered in the
// browser. The boot script sets the root attribute before the first paint, so these checks read the attribute rather than colours.
const STORAGE_KEY = 'jordylab.theme';

async function activeTheme(page: Page): Promise<string | null> {
  return page.evaluate(() => document.documentElement.getAttribute('data-theme'));
}

async function openAppSettings(page: Page): Promise<void> {
  await page.goto('/games/grid');
  await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'App' }).click();
  await expect(page.getByRole('heading', { level: 2, name: 'App' })).toBeVisible();
}

test.describe('Themes', () => {
  test.beforeEach(async ({ page }) => {
    // Start from "no stored choice" once per tab; a later reload must keep what the test chose in between.
    await page.addInitScript((key) => {
      if (window.sessionStorage.getItem('e2e-theme-reset')) {
        return;
      }
      window.sessionStorage.setItem('e2e-theme-reset', '1');
      window.localStorage.removeItem(key);
    }, STORAGE_KEY);
  });

  test('a dark device with no stored choice shows Content Led', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/games/grid');

    expect(await activeTheme(page)).toBe('content');
  });

  test('a light device with no stored choice shows Paper', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/games/grid');

    expect(await activeTheme(page)).toBe('paper');
  });

  test('a chosen theme applies at once and survives a reload', async ({ page }) => {
    await openAppSettings(page);

    await page.locator('label', { hasText: 'Heather' }).click();

    await expect.poll(() => activeTheme(page)).toBe('heather');
    await page.reload();
    await expect(page.getByRole('heading', { level: 2, name: 'App' })).toBeVisible();
    expect(await activeTheme(page)).toBe('heather');
    expect(await page.evaluate((key) => window.localStorage.getItem(key), STORAGE_KEY)).toBe('heather');
  });

  test('following the device switches the app when the device setting changes, without a reload', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await openAppSettings(page);
    await page.locator('label', { hasText: 'Oxblood' }).click();
    await page.locator('label', { hasText: 'Follow device' }).click();
    await expect.poll(() => activeTheme(page)).toBe('content');

    await page.emulateMedia({ colorScheme: 'light' });

    await expect.poll(() => activeTheme(page)).toBe('paper');
  });

  test('a stored light choice is on the page before the app starts and never flickers to another theme', async ({ page }) => {
    await page.addInitScript((key) => {
      window.localStorage.setItem(key, 'glasshouse');
      const observed: (string | null)[] = [];
      (window as unknown as { observedThemes: (string | null)[] }).observedThemes = observed;
      new MutationObserver(() => observed.push(document.documentElement.getAttribute('data-theme'))).observe(document, {
        attributes: true,
        subtree: true,
        attributeFilter: ['data-theme'],
      });
    }, STORAGE_KEY);

    await page.goto('/games/grid');
    await expect(page.getByRole('heading', { level: 2, name: 'Library' })).toBeVisible();

    const observed = await page.evaluate(() => (window as unknown as { observedThemes: (string | null)[] }).observedThemes);
    expect(observed.length).toBeGreaterThan(0);
    expect(new Set(observed)).toEqual(new Set(['glasshouse']));
  });

  test('a guest can open Settings → App and choose a theme too', async ({ browser, page }) => {
    const guest = await signedInApprovedGuest(browser, page, environment);
    await guest.page.goto('/games/grid');
    await guest.page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'App' }).click();
    await expect(guest.page.getByRole('heading', { level: 2, name: 'App' })).toBeVisible();

    await guest.page.locator('label', { hasText: /^Paper$/ }).click();

    await expect.poll(() => activeTheme(guest.page)).toBe('paper');
    await guest.context.close();
  });
});
