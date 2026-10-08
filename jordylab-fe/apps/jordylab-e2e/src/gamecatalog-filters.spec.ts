import { expect, test } from './support/fixtures';

// The library bar is calm by default (search + one Filters button) and shows every filter in use as a removable chip. The state
// lives in the address bar, so a reload brings it back. Platforms come from the scan the setup delivered (Super Nintendo x2,
// Nintendo 64 x1), so the numbers below follow from that catalog.
test.describe('Library filters', () => {
  test('by default only the search box and one Filters button show, plus the Installed status chip', async ({ page }) => {
    await page.goto('/games/grid');

    await expect(page.getByLabel('Search games by title')).toBeVisible();
    await expect(page.getByRole('button', { name: /^Filters/ })).toBeVisible();
    await expect(page.getByRole('dialog', { name: 'Filters' })).toHaveCount(0);
    await expect(page.getByTestId('active-filter')).toHaveCount(1);
    await expect(page.getByTestId('active-filter')).toContainText('Installed');
  });

  test('choosing a platform and a player count shows removable chips and narrows the library; a reload keeps them', async ({ page }) => {
    await page.goto('/games/grid');
    await page.getByRole('button', { name: /^Filters/ }).click();
    const panel = page.getByRole('dialog', { name: 'Filters' });

    await panel.getByRole('button', { name: 'Nintendo 64' }).click();
    await panel.getByRole('button', { name: 'More players' }).click();
    await panel.getByTestId('filters-done').click();

    await expect(page.getByTestId('active-filter').filter({ hasText: 'Nintendo 64' })).toBeVisible();
    await expect(page.getByTestId('active-filter').filter({ hasText: '2+' })).toBeVisible();
    await expect(page).toHaveURL(/platform=Nintendo(\+|%20)64/);
    await expect(page.getByRole('heading', { level: 3, name: 'Chrono Trigger' })).toHaveCount(0);

    await page.reload();

    await expect(page.getByTestId('active-filter').filter({ hasText: 'Nintendo 64' })).toBeVisible();
    await expect(page.getByTestId('active-filter').filter({ hasText: '2+' })).toBeVisible();
    await expect(page.getByRole('button', { name: /^Filters/ })).toContainText('3');
  });

  test('removing one chip leaves the others and Clear all removes every filter', async ({ page }) => {
    await page.goto('/games/grid?platform=Nintendo%2064&players=3');
    await expect(page.getByTestId('active-filter')).toHaveCount(3);

    await page.getByRole('button', { name: 'Remove filter Players: 3+' }).click();
    await expect(page.getByTestId('active-filter')).toHaveCount(2);
    await expect(page.getByTestId('result-count')).toContainText('game');

    await page.getByTestId('clear-all-filters').click();

    await expect(page.getByTestId('active-filter')).toHaveCount(0);
    for (const title of ['Chrono Trigger', 'Super Metroid', 'Ocarina of Time']) {
      await expect(page.getByRole('heading', { level: 3, name: title })).toBeVisible();
    }
  });

  test('an empty result names the filter to relax', async ({ page }) => {
    await page.goto('/games/grid?platform=Nintendo%2064&status=NOT_INSTALLED');

    await expect(page.getByTestId('empty-library')).toContainText('No games match these filters');
    await page.getByRole('button', { name: /Try removing/ }).click();

    await expect(page.getByTestId('empty-library')).toHaveCount(0);
  });

  test('works from the keyboard: Enter opens the panel, focus moves in, Escape closes and returns focus', async ({ page }) => {
    await page.goto('/games/grid');
    const filtersButton = page.getByRole('button', { name: /^Filters/ });

    await filtersButton.focus();
    await page.keyboard.press('Enter');

    await expect(page.getByRole('dialog', { name: 'Filters' })).toBeVisible();
    await expect(filtersButton).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByRole('dialog', { name: 'Filters' }).locator(':focus')).toHaveCount(1);

    await page.keyboard.press('Escape');

    await expect(page.getByRole('dialog', { name: 'Filters' })).toHaveCount(0);
    await expect(filtersButton).toBeFocused();
  });

  test.describe('on a phone', () => {
    test.use({ viewport: { width: 360, height: 780 } });

    test('the panel is a bottom sheet and the page never scrolls sideways', async ({ page }) => {
      await page.goto('/games/grid');
      await page.getByRole('button', { name: /^Filters/ }).click();
      const panel = page.getByRole('dialog', { name: 'Filters' });
      await expect(panel).toBeVisible();

      // Panel and viewport are measured in one synchronous call: a classic page scrollbar (CI) takes a few
      // pixels from the viewport, and may appear or disappear while the sheet opens.
      const measureSheet = (): Promise<{ left: number; widthGap: number; bottomGap: number }> =>
        page.evaluate(() => {
          const rect = document.querySelector('[data-testid="filters-panel"]')?.getBoundingClientRect();
          const visibleWidth = document.documentElement.clientWidth;

          return {
            left: rect?.left ?? -1,
            widthGap: Math.abs((rect?.width ?? 0) - visibleWidth),
            bottomGap: Math.abs((rect?.bottom ?? 0) - window.innerHeight),
          };
        });
      await expect.poll(measureSheet).toEqual({ left: 0, widthGap: 0, bottomGap: 0 });

      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow).toBeLessThanOrEqual(0);
    });
  });
});
