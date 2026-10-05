import AxeBuilder from '@axe-core/playwright';
import type { Page } from '@playwright/test';
import { environment, expect, test } from './support/fixtures';
import { PAGES } from './support/pages';
import { pointAppAtKeycloak } from './support/session';

// Automated accessibility check (WCAG 2.x A and AA: colour contrast, accessible names, labels, landmarks) on every signed-in page, on a
// desktop and a phone-sized screen (the Android app shows this same web build). It exists because contrast and "label in name" findings
// (BUG-064) only showed up when the owner ran Lighthouse by hand on a single page. A failure lists each rule, how many elements it hit and
// a CSS selector for the first few; the page data comes from the same throwaway catalog the other journeys use.
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

const SCREENS = [
  { name: 'desktop', viewport: { width: 1280, height: 800 } },
  { name: 'phone', viewport: { width: 390, height: 844 } },
] as const;

async function violationsOf(page: Page): Promise<string[]> {
  await page.emulateMedia({ reducedMotion: 'reduce' }); // a half-finished fade would make the contrast numbers meaningless
  await page.waitForLoadState('networkidle');
  const { violations } = await new AxeBuilder({ page })
    .withTags(WCAG_TAGS)
    .analyze();

  return violations.map((violation) => {
    const targets = violation.nodes.slice(0, 3).map((node) => node.target.join(' '));
    const detail = violation.nodes[0]?.any[0]?.message ?? violation.help; // for colour contrast this carries the measured ratio and both colours
    return `${violation.id} (${violation.impact}): ${detail} — ${violation.nodes.length} element(s), e.g. ${targets.join(' | ')}`;
  });
}

test.describe('Accessibility (WCAG 2 A and AA)', () => {
  for (const screen of SCREENS) {
    test.describe(screen.name, () => {
      test.use({ viewport: screen.viewport });

      for (const target of PAGES) {
        test(`${target.name} has no violations`, async ({ page }) => {
          await page.goto(target.path);
          await expect(
            page.getByRole('heading', { name: target.heading }).first(),
          ).toBeVisible();

          expect(await violationsOf(page)).toEqual([]);
        });
      }

      test('a game detail page has no violations', async ({ page }) => {
        await page.goto('/games/grid');
        await page.getByRole('link', { name: /Chrono Trigger/ }).click();
        await expect(
          page.getByRole('heading', { level: 2, name: 'Chrono Trigger' }),
        ).toBeVisible();

        expect(await violationsOf(page)).toEqual([]);
      });
    });
  }

  test('the login page has no violations', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: environment.baseUrl, storageState: undefined });
    await pointAppAtKeycloak(context, environment.keycloakUrl);
    const page = await context.newPage();
    await page.goto('/login');
    await expect(
      page.getByRole('button', { name: 'Sign in with Keycloak' }),
    ).toBeVisible();

    expect(await violationsOf(page)).toEqual([]);
    await context.close();
  });
});
