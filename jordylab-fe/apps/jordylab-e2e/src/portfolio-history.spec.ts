import { expect, test } from './support/fixtures';

// A holding is saved even when its euro price cannot be resolved, so this journey does not depend on the price source
// being reachable from the e2e stack. The panel shows the period choice either way; the history itself may still be empty.
test.describe('Portfolio price history', () => {
  test('a holding opens its history, the period changes, and closing returns focus to its row', async ({
    page,
  }) => {
    await page.goto('/fna/portfolio');

    await page
      .getByRole('textbox', { name: 'Ticker', exact: true })
      .fill('ASML');
    await page
      .getByRole('spinbutton', { name: 'Shares', exact: true })
      .fill('2');
    await page
      .getByRole('button', { name: 'Add position', exact: true })
      .click();

    await page
      .getByRole('button', { name: 'History for ASML', exact: true })
      .click();

    const panel = page.getByTestId('price-history-panel');
    await expect(
      panel.getByRole('heading', { name: 'ASML', exact: true }),
    ).toBeFocused();
    await expect(panel.getByRole('group', { name: 'Period' })).toBeVisible();
    await expect(
      panel.getByRole('button', { name: '1Y', exact: true }),
    ).toHaveAttribute('aria-pressed', 'true');

    await panel.getByRole('button', { name: '3M', exact: true }).click();
    await expect(
      panel.getByRole('button', { name: '3M', exact: true }),
    ).toHaveAttribute('aria-pressed', 'true');

    await panel.getByRole('button', { name: 'Close', exact: true }).click();
    await expect(page.getByTestId('price-history-panel')).toHaveCount(0);
    await expect(
      page.getByRole('button', { name: 'History for ASML', exact: true }),
    ).toBeFocused();
  });
});
