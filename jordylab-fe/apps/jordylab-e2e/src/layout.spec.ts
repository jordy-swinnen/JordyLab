import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';
import { PAGES } from './support/pages';

// No page may scroll sideways on a phone (spec 013 SC-019): the installed phone app (a PWA, spec 018) shows this same web build. The check measures the
// document and, when it fails, names the elements that stick out so the fix is one look away rather than a bisect.
const PHONE_WIDTHS = [360, 390, 430] as const;

async function offendersOf(page: Page): Promise<string[]> {
  await page.waitForLoadState('networkidle');

  return page.evaluate(() => {
    const viewportWidth = document.documentElement.clientWidth;
    if (document.documentElement.scrollWidth <= viewportWidth) {
      return [];
    }
    const describe = (element: Element) => {
      const testId = element.getAttribute('data-testid');
      const id = element.id ? `#${element.id}` : '';
      const classes = typeof element.className === 'string' ? element.className.split(/\s+/).filter(Boolean).slice(0, 3).join('.') : '';

      return `${element.tagName.toLowerCase()}${id}${classes ? `.${classes}` : ''}${testId ? `[data-testid=${testId}]` : ''}`;
    };

    return [
      `scrollWidth ${document.documentElement.scrollWidth} > clientWidth ${viewportWidth}`,
      ...Array.from(document.body.querySelectorAll('*'))
        .filter((element) => element.getBoundingClientRect().right > viewportWidth + 1)
        .slice(0, 8)
        .map((element) => `${describe(element)} ends at ${Math.round(element.getBoundingClientRect().right)}px`),
    ];
  });
}

// Adds a holding through the form (the price may stay unresolved; the holding is still saved).
async function addHolding(page: Page, ticker: string): Promise<void> {
  await page.goto('/fna/portfolio');
  await page.getByRole('textbox', { name: 'Ticker', exact: true }).fill(ticker);
  await page.getByRole('spinbutton', { name: 'Shares', exact: true }).fill('2');
  await page.getByRole('button', { name: 'Add position', exact: true }).click();
  await expect(page.getByRole('button', { name: `History for ${ticker}`, exact: true })).toBeVisible();
}

test.describe('No sideways scrolling on a phone', () => {
  for (const width of PHONE_WIDTHS) {
    test.describe(`${width} px wide`, () => {
      test.use({ viewport: { width, height: 844 } });

      for (const target of PAGES) {
        test(`${target.name} fits the screen`, async ({ page }) => {
          await page.goto(target.path);
          await expect(page.getByRole('heading', { name: target.heading }).first()).toBeVisible();

          expect(await offendersOf(page)).toEqual([]);
        });
      }

      test.describe('touch targets', () => {
        for (const target of PAGES) {
          test(`${target.name} has no control smaller than 44 px`, async ({ page }) => {
            await page.goto(target.path);
            await expect(page.getByRole('heading', { name: target.heading }).first()).toBeVisible();
            await page.waitForLoadState('networkidle');

            const tooSmall = await page.evaluate(() => {
              const selector = 'button, a[href], [role=switch], [role=radio], input:not([type=hidden]), select';
              const describe = (element: Element) => {
                const label = (element.getAttribute('aria-label') ?? element.textContent ?? '').trim().slice(0, 30);

                return `${element.tagName.toLowerCase()}${element.getAttribute('data-testid') ? `[data-testid=${element.getAttribute('data-testid')}]` : ''} "${label}"`;
              };
              const insideRunningText = (element: Element) => element.tagName === 'A' && element.closest('p, li, dd, article > div') !== null && getComputedStyle(element).display === 'inline';

              return Array.from(document.body.querySelectorAll(selector))
                .filter((element) => {
                  const box = element.getBoundingClientRect();
                  const visible = box.width > 0 && box.height > 0 && getComputedStyle(element).visibility !== 'hidden';

                  return visible && !insideRunningText(element) && !element.classList.contains('sr-only') && (box.height < 43.5 || box.width < 43.5);
                })
                .slice(0, 12)
                .map((element) => `${describe(element)} is ${Math.round(element.getBoundingClientRect().width)}x${Math.round(element.getBoundingClientRect().height)}`);
            });

            expect(tooSmall).toEqual([]);
          });
        }
      });

      test('the library with the Filters panel open fits the screen', async ({ page }) => {
        await page.goto('/games/grid');
        await page.getByRole('button', { name: /^Filters/ }).click();
        await expect(page.getByRole('dialog', { name: 'Filters' })).toBeVisible();

        expect(await offendersOf(page)).toEqual([]);
      });

      test('a game with one very long word in its title fits the screen', async ({ page }) => {
        await page.goto('/games/grid');
        await page.getByRole('link', { name: /Supercalifragilistic/ }).click();
        await expect(page.getByRole('heading', { level: 2, name: /Supercalifragilistic/ })).toBeVisible();

        expect(await offendersOf(page)).toEqual([]);
      });

      test('a game page fits the screen', async ({ page }) => {
        await page.goto('/games/grid');
        await page.getByRole('link', { name: /Chrono Trigger/ }).click();
        await expect(page.getByRole('heading', { level: 2, name: 'Chrono Trigger' })).toBeVisible();

        expect(await offendersOf(page)).toEqual([]);
      });

      // offendersOf passes when the page itself does not scroll, even if a table clips content inside its own scroll box,
      // so this measures the History button of a wishlist row against the screen edge. Each width adds its own ticker
      // because the e2e database is shared by every test of a run.
      test('the History button of a wishlist row is on the screen', async ({ page }) => {
        const wishlistTicker = { 360: 'LMT', 390: 'RHM', 430: 'MEUD' }[width];
        await page.goto('/fna/portfolio');
        await page.getByRole('textbox', { name: 'Wishlist ticker', exact: true }).fill(wishlistTicker);
        await page.getByRole('button', { name: 'Add to wishlist', exact: true }).last().click();

        const historyButton = page.getByRole('button', { name: `History for ${wishlistTicker}`, exact: true });
        await expect(historyButton).toBeVisible();
        const table = page.getByRole('region', { name: 'Wishlist table', exact: true });
        expect(await table.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true);
        const box = await historyButton.boundingBox();

        expect(box).not.toBeNull();
        expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual(width);
      });

      // Holdings are checked the same way as wishlist rows; each width adds its own holding ticker because the e2e database
      // is shared by every test of a run.
      test('the History button of a holding is on the screen', async ({ page }) => {
        const holdingTicker = { 360: 'ABI', 390: 'SAP', 430: 'SIE' }[width];
        await page.goto('/fna/portfolio');
        await page.getByRole('textbox', { name: 'Ticker', exact: true }).fill(holdingTicker);
        await page.getByRole('spinbutton', { name: 'Shares', exact: true }).fill('2');
        await page.getByRole('button', { name: 'Add position', exact: true }).click();

        const historyButton = page.getByRole('button', { name: `History for ${holdingTicker}`, exact: true });
        await expect(historyButton).toBeVisible();
        const table = page.getByRole('region', { name: 'Positions table', exact: true });
        expect(await table.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true);
        const box = await historyButton.boundingBox();

        expect(box).not.toBeNull();
        expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual(width);
      });

      // The History panel replaces the holding's row, so it must fit the screen the same way (spec 023).
      test('a holding with its history open fits the screen', async ({ page }) => {
        const holdingTicker = { 360: 'INTC', 390: 'BAYN', 430: 'TSLA' }[width];
        await addHolding(page, holdingTicker);
        await page.getByRole('button', { name: `History for ${holdingTicker}`, exact: true }).click();
        await expect(page.getByTestId('price-history-panel')).toBeVisible();

        expect(await offendersOf(page)).toEqual([]);
      });

      // The four period buttons must share one row, or the choice wraps and the panel grows on a phone (spec 023 T046).
      test('the period buttons of an open history sit on one row', async ({ page }) => {
        const holdingTicker = { 360: 'CRM', 390: 'UBER', 430: 'PFE' }[width];
        await addHolding(page, holdingTicker);
        await page.getByRole('button', { name: `History for ${holdingTicker}`, exact: true }).click();
        const periodButtons = page.getByRole('group', { name: 'Period', exact: true }).getByRole('button');

        await expect(periodButtons).toHaveCount(4);
        const rowTops = await periodButtons.evaluateAll((buttons) =>
          buttons.map((button) => Math.round(button.getBoundingClientRect().top)),
        );
        expect(new Set(rowTops).size).toBe(1);
      });

      // Phones have no hover, and Remove is hidden until hover only from 640 px up, so it must show here without hover.
      test('the Remove button of a holding shows without hover', async ({ page }) => {
        const holdingTicker = { 360: 'NFLX', 390: 'ORCL', 430: 'AMZN' }[width];
        await addHolding(page, holdingTicker);
        const remove = page
          .getByRole('region', { name: 'Positions table', exact: true })
          .locator('tr', { hasText: holdingTicker })
          .getByRole('button', { name: 'Remove', exact: true });

        await expect(remove).toHaveCSS('opacity', '1');
      });
    });
  }
});
