import type { Page } from '@playwright/test';
import { environment, expect, test } from './support/fixtures';
import { signedInApprovedGuest } from './support/guest';

// Two people vote on the same games through the app: the admin and a guest who signed up and was approved. Each holds at most one
// mark per game, the totals are public and equal for both, and replacing or clearing a mark moves the totals.
function card(page: Page, title: string) {
  return page.getByTestId('game-card').filter({ has: page.getByRole('heading', { level: 3, name: title }) });
}

async function totalsOnGamePage(page: Page, title: string) {
  await page.goto('/games/grid');
  await page.getByRole('link', { name: new RegExp(title) }).click();
  await expect(page.getByRole('heading', { level: 2, name: title })).toBeVisible();

  return page.getByTestId('mark-totals');
}

test.describe('Marks', () => {
  test('two people vote on overlapping games; totals are shared, replaced and cleared', async ({ browser, page }) => {
    const guest = await signedInApprovedGuest(browser, page, environment);
    try {
      await page.goto('/games/grid');
      await guest.page.goto('/games/grid');

      // The admin wants Chrono Trigger; the guest played and liked it.
      await card(page, 'Chrono Trigger').getByRole('button', { name: 'Want to play' }).click();
      await expect(card(page, 'Chrono Trigger').getByRole('button', { name: 'Want to play' })).toHaveAttribute('aria-pressed', 'true');
      await card(guest.page, 'Chrono Trigger').getByRole('button', { name: 'Played & liked' }).click();
      await expect(card(guest.page, 'Chrono Trigger').getByRole('button', { name: 'Played & liked' })).toHaveAttribute('aria-pressed', 'true');

      // Both see one want and one like on the game page, never who gave them.
      for (const person of [page, guest.page]) {
        const totals = await totalsOnGamePage(person, 'Chrono Trigger');
        await expect(totals.getByRole('img', { name: /^Want to play: 1/ })).toBeVisible();
        await expect(totals.getByRole('img', { name: /^Played & liked: 1/ })).toBeVisible();
        await expect(totals.getByRole('img', { name: /^Played & disliked: 0/ })).toBeVisible();
        await expect(person.getByTestId('marks-section').getByText(guest.email)).toHaveCount(0);
      }

      // Replacing: the guest changes their like to a want. Totals move, still one vote each.
      await totalsOnGamePage(guest.page, 'Chrono Trigger');
      await guest.page.getByTestId('mark-buttons').getByRole('button', { name: 'Want to play' }).click();
      const afterReplace = guest.page.getByTestId('mark-totals');
      await expect(afterReplace.getByRole('img', { name: /^Want to play: 2/ })).toBeVisible();
      await expect(afterReplace.getByRole('img', { name: /^Played & liked: 0/ })).toBeVisible();

      // Clearing: pressing the mark they hold removes it.
      await guest.page.getByTestId('mark-buttons').getByRole('button', { name: 'Want to play' }).click();
      await expect(afterReplace.getByRole('img', { name: /^Want to play: 1/ })).toBeVisible();

      // The admin sees the cleared total after a reload.
      const adminTotals = await totalsOnGamePage(page, 'Chrono Trigger');
      await expect(adminTotals.getByRole('img', { name: /^Want to play: 1/ })).toBeVisible();
    } finally {
      await guest.context.close();
    }
  });

  test('the Community marks filter narrows the library to my own marks', async ({ page }) => {
    await page.goto('/games/grid');
    await card(page, 'Super Metroid').getByRole('button', { name: 'Played & disliked' }).click();
    await expect(card(page, 'Super Metroid').getByRole('button', { name: 'Played & disliked' })).toHaveAttribute('aria-pressed', 'true');

    await page.goto('/games/grid?status=ALL&mark=PLAYED_DISLIKED&scope=MINE');

    await expect(page.getByTestId('active-filter').filter({ hasText: 'My marks' })).toContainText('Played & disliked');
    await expect(page.getByRole('heading', { level: 3, name: 'Super Metroid' })).toBeVisible();
    await expect(page.getByRole('heading', { level: 3, name: 'Chrono Trigger' })).toHaveCount(0);
  });
});
