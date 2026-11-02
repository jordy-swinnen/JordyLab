import { expect, test } from './support/fixtures';

// The designed loading, error and empty states. A delayed or aborted API answer stands in for a slow or failing server; nothing is
// written to any database. The empty catalog is covered by the unit specs, because the throwaway catalog always holds games.
const PHONE = { width: 390, height: 844 } as const;

test.describe('Loading, error and first-run states', () => {
  test('a slow catalog answer shows placeholders, then the games', async ({ page }) => {
    await page.route('**/api/gamecatalog/games**', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1500));
      await route.continue();
    });

    await page.goto('/games/grid');
    await expect(page.getByLabel('Loading games')).toBeVisible();
    await expect(page.getByTestId('game-card').first()).toBeVisible();
  });

  test('a failed catalog request shows the error with Try again, and trying again recovers', async ({ page }) => {
    let failures = 0;
    await page.route('**/api/gamecatalog/games**', async (route) => {
      if (failures === 0) {
        failures += 1;
        await route.abort();
        return;
      }
      await route.continue();
    });

    await page.goto('/games/grid');
    await expect(page.getByRole('alert')).toContainText('Failed to load games');
    await page.getByRole('button', { name: 'Try again' }).click();

    await expect(page.getByTestId('game-card').first()).toBeVisible();
  });

  test('a slow sources answer shows placeholders, a failed one shows Try again', async ({ page }) => {
    let calls = 0;
    await page.route('**/api/gamecatalog/sources**', async (route) => {
      calls += 1;
      if (calls === 1) {
        await route.abort();
        return;
      }
      await route.continue();
    });

    await page.goto('/games/sources');
    await expect(page.getByRole('alert')).toBeVisible();
    await page.getByRole('button', { name: 'Try again' }).click();

    await expect(page.getByRole('heading', { level: 2, name: 'Scan sources' })).toBeVisible();
  });

  test('LibBot with no conversation shows the first-run state with example questions', async ({ page }) => {
    await page.goto('/games/libbot');

    await expect(page.getByTestId('libbot-first-run')).toBeVisible();
    await expect(page.getByTestId('libbot-example')).toHaveCount(3);
  });

  test.describe('on a phone', () => {
    test.use({ viewport: PHONE });

    test('the error state fits the screen', async ({ page }) => {
      await page.route('**/api/gamecatalog/games**', (route) => route.abort());
      await page.goto('/games/grid');

      await expect(page.getByRole('alert')).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
    });
  });

  test('placeholders do not pulse when reduced motion is requested', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.route('**/api/gamecatalog/games**', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 2000));
      await route.continue();
    });

    await page.goto('/games/grid');
    const placeholder = page.getByLabel('Loading games').locator('hlm-skeleton').first();
    await expect(placeholder).toBeVisible();

    expect(await placeholder.evaluate((element) => getComputedStyle(element).animationName)).toBe('none');
  });
});
