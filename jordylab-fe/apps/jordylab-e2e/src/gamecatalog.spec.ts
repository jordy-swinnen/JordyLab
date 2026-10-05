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
    await expect(page.getByText('Super Nintendo').first()).toBeVisible();
    await expect(page.getByText(/Installed on · e2e-host/)).toBeVisible();

    await page.getByRole('link', { name: /Back to library/ }).click();
    await expect(page.getByRole('heading', { level: 2, name: 'Library' })).toBeVisible();
  });
});
