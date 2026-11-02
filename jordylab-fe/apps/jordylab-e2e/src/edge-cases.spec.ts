import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';

// The edge cases of the redesign: the font file failing to load, a high-contrast (forced colours) browser, and a very narrow screen.
const sidewaysScroll = (page: Page) => page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);

test.describe('Edge cases', () => {
  test('with the font file blocked the Library and App settings still show readable text and do not scroll sideways', async ({ page }) => {
    await page.route('**/*.woff2', (route) => route.abort());
    await page.setViewportSize({ width: 390, height: 844 });

    await page.goto('/games/grid');
    await expect(page.getByRole('heading', { level: 2, name: 'Library' })).toBeVisible();
    await expect(page.getByTestId('game-card').first()).toBeVisible();
    expect(await sidewaysScroll(page)).toBe(false);
    expect(await page.getByRole('heading', { level: 2, name: 'Library' }).evaluate((element) => getComputedStyle(element).fontFamily)).toContain('system-ui');

    await page.goto('/settings/app');
    await expect(page.getByRole('heading', { level: 2, name: 'App' })).toBeVisible();
    expect(await sidewaysScroll(page)).toBe(false);
  });

  test('in forced-colours mode the buttons, links and the theme picker stay visible and identifiable', async ({ page }) => {
    await page.emulateMedia({ forcedColors: 'active' });

    await page.goto('/settings/app');
    await expect(page.getByRole('heading', { level: 2, name: 'App' })).toBeVisible();
    await expect(page.getByRole('radio').first()).toBeVisible();
    expect(await page.getByRole('radio').count()).toBeGreaterThanOrEqual(7);

    await page.goto('/games/grid');
    await expect(page.getByRole('button', { name: /^Filters/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Chrono Trigger/ })).toBeVisible();
  });

  for (const path of ['/games/grid', '/settings/app']) {
    test(`${path} does not scroll sideways at 320 px`, async ({ page }) => {
      await page.setViewportSize({ width: 320, height: 640 });
      await page.goto(path);
      await expect(page.getByRole('heading', { level: 2 }).first()).toBeVisible();
      await page.waitForLoadState('networkidle');

      expect(await sidewaysScroll(page)).toBe(false);
    });
  }
});
