import { expect, test } from './support/fixtures';

// The throwaway backend has no AI provider keys, so this journey covers the chat up to the model call: the question is sent, it
// appears in the conversation, and the app answers with its graceful "unavailable" message instead of failing. No paid call is made.
test.describe('Catalog chat (up to the model call)', () => {
  test('asking a question shows it and the unavailable message', async ({ page }) => {
    await page.goto('/games/chat');
    await expect(page.getByRole('heading', { level: 2, name: 'Ask the Catalog' })).toBeVisible();

    const question = 'Which games support 4-player local co-op?';
    await page.getByLabel('Ask a question about your games').fill(question);
    await page.getByRole('button', { name: 'Ask' }).click();

    await expect(page.getByText(question)).toBeVisible();
    await expect(page.getByText('Chat is currently unavailable. Please try again later.')).toBeVisible();
  });
});
