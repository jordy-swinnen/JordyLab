import { expect, test } from './support/fixtures';

// The throwaway backend has no AI provider keys, so this journey covers LibBot up to the model call: the page renders, the
// question is sent and shown, and the app answers with its graceful "unavailable" state and a Try again action instead of
// failing. No paid call is made, and the failed question is not counted against any message allowance.
test.describe('LibBot (up to the model call)', () => {
  test('asking a question shows it and the unavailable message with Try again', async ({ page }) => {
    await page.goto('/games/libbot');
    await expect(page.getByRole('heading', { level: 2, name: 'LibBot' })).toBeVisible();

    const question = 'There are 6 people here tonight, what should we play?';
    await page.getByLabel('Ask LibBot a question about your games').fill(question);
    await page.getByRole('button', { name: 'Ask', exact: true }).click();

    await expect(page.getByText(question)).toBeVisible();
    await expect(page.getByTestId('libbot-error')).toContainText('LibBot can’t answer right now');
    await expect(page.getByTestId('libbot-retry')).toBeVisible();
    await expect(page.getByLabel('Ask LibBot a question about your games')).toHaveValue(question);
  });

  test('New conversation clears the thread', async ({ page }) => {
    await page.goto('/games/libbot');
    await page.getByLabel('Ask LibBot a question about your games').fill('hello');
    await page.getByRole('button', { name: 'Ask', exact: true }).click();
    await expect(page.getByTestId('libbot-user-message')).toHaveCount(1);

    await page.getByTestId('libbot-new-conversation').click();

    await expect(page.getByTestId('libbot-user-message')).toHaveCount(0);
  });
});
