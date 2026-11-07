import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';
import { PAGES } from './support/pages';

// No page may scroll sideways on a phone (spec 013 SC-019): the Android app shows this same web build. The check measures the
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
      const classes =
        typeof element.className === 'string'
          ? element.className.split(/\s+/).filter(Boolean).slice(0, 3).join('.')
          : '';

      return `${element.tagName.toLowerCase()}${id}${classes ? `.${classes}` : ''}${testId ? `[data-testid=${testId}]` : ''}`;
    };

    return [
      `scrollWidth ${document.documentElement.scrollWidth} > clientWidth ${viewportWidth}`,
      ...Array.from(document.body.querySelectorAll('*'))
        .filter(
          (element) =>
            element.getBoundingClientRect().right > viewportWidth + 1,
        )
        .slice(0, 8)
        .map(
          (element) =>
            `${describe(element)} ends at ${Math.round(element.getBoundingClientRect().right)}px`,
        ),
    ];
  });
}

test.describe('No sideways scrolling on a phone', () => {
  for (const width of PHONE_WIDTHS) {
    test.describe(`${width} px wide`, () => {
      test.use({ viewport: { width, height: 844 } });

      for (const target of PAGES) {
        test(`${target.name} fits the screen`, async ({ page }) => {
          await page.goto(target.path);
          await expect(
            page.getByRole('heading', { name: target.heading }).first(),
          ).toBeVisible();

          expect(await offendersOf(page)).toEqual([]);
        });
      }

      test.describe('touch targets', () => {
        for (const target of PAGES) {
          test(`${target.name} has no control smaller than 44 px`, async ({
            page,
          }) => {
            await page.goto(target.path);
            await expect(
              page.getByRole('heading', { name: target.heading }).first(),
            ).toBeVisible();
            await page.waitForLoadState('networkidle');

            const tooSmall = await page.evaluate(() => {
              const selector =
                'button, a[href], [role=switch], [role=radio], input:not([type=hidden]), select';
              const describe = (element: Element) => {
                const label = (
                  element.getAttribute('aria-label') ??
                  element.textContent ??
                  ''
                )
                  .trim()
                  .slice(0, 30);

                return `${element.tagName.toLowerCase()}${element.getAttribute('data-testid') ? `[data-testid=${element.getAttribute('data-testid')}]` : ''} "${label}"`;
              };
              const insideRunningText = (element: Element) =>
                element.tagName === 'A' &&
                element.closest('p, li, dd, article > div') !== null &&
                getComputedStyle(element).display === 'inline';

              return Array.from(document.body.querySelectorAll(selector))
                .filter((element) => {
                  const box = element.getBoundingClientRect();
                  const visible =
                    box.width > 0 &&
                    box.height > 0 &&
                    getComputedStyle(element).visibility !== 'hidden';

                  return (
                    visible &&
                    !insideRunningText(element) &&
                    !element.classList.contains('sr-only') &&
                    (box.height < 43.5 || box.width < 43.5)
                  );
                })
                .slice(0, 12)
                .map(
                  (element) =>
                    `${describe(element)} is ${Math.round(element.getBoundingClientRect().width)}x${Math.round(element.getBoundingClientRect().height)}`,
                );
            });

            expect(tooSmall).toEqual([]);
          });
        }
      });

      test('the library with the Filters panel open fits the screen', async ({
        page,
      }) => {
        await page.goto('/games/grid');
        await page.getByRole('button', { name: /^Filters/ }).click();
        await expect(
          page.getByRole('dialog', { name: 'Filters' }),
        ).toBeVisible();

        expect(await offendersOf(page)).toEqual([]);
      });

      test('a game with one very long word in its title fits the screen', async ({
        page,
      }) => {
        await page.goto('/games/grid');
        await page.getByRole('link', { name: /Supercalifragilistic/ }).click();
        await expect(
          page.getByRole('heading', { level: 2, name: /Supercalifragilistic/ }),
        ).toBeVisible();

        expect(await offendersOf(page)).toEqual([]);
      });

      test('a game page fits the screen', async ({ page }) => {
        await page.goto('/games/grid');
        await page.getByRole('link', { name: /Chrono Trigger/ }).click();
        await expect(
          page.getByRole('heading', { level: 2, name: 'Chrono Trigger' }),
        ).toBeVisible();

        expect(await offendersOf(page)).toEqual([]);
      });

      // offendersOf passes when the page itself does not scroll, even if a table clips content inside its own scroll box,
      // so this measures the History button of a wishlist row against the screen edge.
      test('the History button of a wishlist row is on the screen', async ({
        page,
      }) => {
        await page.goto('/fna/portfolio');
        await page
          .getByRole('textbox', { name: 'Wishlist ticker', exact: true })
          .fill('LMT');
        await page
          .getByRole('button', { name: 'Add to wishlist', exact: true })
          .last()
          .click();

        const historyButton = page.getByRole('button', {
          name: 'History for LMT',
          exact: true,
        });
        await expect(historyButton).toBeVisible();
        const box = await historyButton.boundingBox();

        expect(box).not.toBeNull();
        expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual(width);
      });
    });
  }
});
