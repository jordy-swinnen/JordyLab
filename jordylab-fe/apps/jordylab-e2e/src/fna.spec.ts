import { expect, test } from './support/fixtures';

// Read-only on purpose: generating a briefing calls a model, so this journey only looks at the empty state.
test.describe('FNA briefing view (read-only)', () => {
  test('the briefing page offers a first briefing and shows no generated one', async ({ page }) => {
    await page.goto('/fna/briefing');

    await expect(page.getByRole('heading', { level: 2, name: 'Investment briefing' })).toBeVisible();
    await expect(page.getByText('No briefing generated yet')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Generate Briefing' })).toBeVisible();
  });
});
