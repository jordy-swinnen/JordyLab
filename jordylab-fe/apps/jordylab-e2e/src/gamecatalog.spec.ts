import { CATALOG_GAMES } from './support/catalog-api';
import { expect, test } from './support/fixtures';

test.describe('Game catalog grid and detail', () => {
  test('the library lists every game the scan delivered', async ({ page }) => {
    await page.goto('/games/grid');

    await expect(page.getByRole('heading', { level: 2, name: 'Library' })).toBeVisible();
    for (const game of CATALOG_GAMES) {
      await expect(page.getByRole('heading', { level: 3, name: game.title })).toBeVisible();
    }
  });

  test('searching by title narrows the library', async ({ page }) => {
    await page.goto('/games/grid');
    await page.getByLabel('Search games by title').fill('Metroid');

    await expect(page.getByRole('heading', { level: 3, name: 'Super Metroid' })).toBeVisible();
    await expect(page.getByRole('heading', { level: 3, name: 'Chrono Trigger' })).toHaveCount(0);
  });

  test('opening a game shows its detail page', async ({ page }) => {
    await page.goto('/games/grid');
    await page.getByRole('link', { name: /Chrono Trigger/ }).click();

    await expect(page).toHaveURL(/\/games\/[0-9a-f-]+$/);
    await expect(page.getByRole('heading', { level: 2, name: 'Chrono Trigger' })).toBeVisible();
    await expect(page.locator('lib-platform-chip', { hasText: 'SNES' })).toBeVisible();
    await expect(page.getByText(/Installed on · e2e-host/)).toBeVisible();

    await page.getByRole('link', { name: /Back to library/ }).click();
    await expect(page.getByRole('heading', { level: 2, name: 'Library' })).toBeVisible();
  });

  test('excluding a game hides it and Sources can include it again', async ({ page }) => {
    await page.goto('/games/grid');
    await page.getByRole('link', { name: /Chrono Trigger/ }).click();
    const gameId = page.url().split('/').pop();

    try {
      await page.getByRole('button', { name: 'Exclude from catalog' }).click();
      await page.getByRole('button', { name: 'Exclude', exact: true }).click();

      await expect(page).toHaveURL(/\/games\/grid$/);
      await expect(page.getByRole('heading', { level: 3, name: 'Chrono Trigger' })).toHaveCount(0);

      await page.goto('/games/sources');
      await expect(page.getByTestId('exclusions-count')).toContainText('1 game');
      await page.locator('[data-testid^="include-excluded-"]').click();

      await page.goto('/games/grid');
      await expect(page.getByRole('heading', { level: 3, name: 'Chrono Trigger' })).toBeVisible();
    } finally {
      // A failed assertion must not leave the e2e catalog with a hidden game for the later journeys.
      if (gameId) {
        await page.goto('/games/sources');
        const include = page.locator('[data-testid^="include-excluded-"]');
        if (await include.count()) {
          await include.first().click();
          await expect(include).toHaveCount(0);
        }
      }
    }
  });
});
