import { environment, expect, test } from './support/fixtures';
import { signedInApprovedGuest } from './support/guest';

// Spec 017: a guest sends feedback from their one Settings tab; the admin triages it in the idea
// box, replies, backlogs it, and accepting without a LINEAR_API_KEY fails gracefully (the
// throwaway stack has no key, like the AI journeys asserting the "unavailable" states).
test.describe('Guest feedback and admin triage', () => {
  test('a guest submits feedback and the admin triages it', async ({ browser, page }) => {
    const guest = await signedInApprovedGuest(browser, page, environment);
    const guestPage = guest.page;

    // The guest's Settings area holds exactly one tab: Feedback.
    await guestPage.goto('/settings');
    await expect(guestPage.getByRole('heading', { name: 'Feedback' })).toBeVisible();
    await expect(guestPage.getByRole('link', { name: 'Users' })).toHaveCount(0);

    const title = `E2E feedback ${Date.now().toString(36)}`;
    await guestPage.getByTestId('feedback-app').selectOption('GAME_CATALOG');
    await guestPage.getByTestId('feedback-type').selectOption('BUG_REPORT');
    await guestPage.getByTestId('feedback-title').fill(title);
    await guestPage.getByTestId('feedback-description').fill('Submitted by the Playwright journey.');
    await guestPage.getByTestId('feedback-submit').click();

    const guestTicket = guestPage.getByTestId('guest-ticket').filter({ hasText: title });
    await expect(guestTicket).toBeVisible();
    await expect(guestTicket.getByTestId('guest-ticket-state')).toHaveText('New');

    // The admin sees it in the idea box, with the sender's name.
    await page.goto('/settings/inbox');
    const adminTicket = page.getByTestId('inbox-ticket').filter({ hasText: title });
    await expect(adminTicket).toBeVisible();
    await expect(adminTicket).toContainText('Voter');

    // Reply (guest-visible) and private note, then backlog the ticket.
    await adminTicket.getByTestId('inbox-texts-toggle').click();
    await adminTicket.getByTestId('inbox-reply').fill('Thanks — keeping it for later.');
    await adminTicket.getByTestId('inbox-private-note').fill('A note the guest never sees.');
    await adminTicket.getByTestId('inbox-save-texts').click();
    await expect(adminTicket.getByText('Thanks — keeping it for later.')).toBeVisible();
    await expect(adminTicket.getByText('A note the guest never sees.')).toBeVisible();

    await adminTicket.getByTestId('inbox-backlog').click();
    await expect(adminTicket.getByTestId('inbox-ticket-state')).toHaveText('Backlog');

    // The guest sees the new status and the reply — and never the private note.
    await guestPage.goto('/settings/feedback');
    await expect(guestTicket.getByTestId('guest-ticket-state')).toHaveText('Backlog');
    await guestTicket.locator('summary').click();
    await expect(guestTicket.getByTestId('guest-ticket-reply')).toContainText('Thanks — keeping it for later.');
    await expect(guestTicket).not.toContainText('A note the guest never sees.');

    // Accept without a Linear key fails gracefully: the ticket stays actionable, the admin sees why.
    await page.goto('/settings/inbox');
    await adminTicket.getByTestId('inbox-accept').click();
    await adminTicket.getByTestId('inbox-accept-confirm').click();
    await expect(page.getByText('Linear is unreachable right now — the ticket stays as it was, so you can retry.')).toBeVisible();
    await expect(adminTicket.getByTestId('inbox-ticket-state')).toHaveText('Backlog');

    await guest.context.close();
  });
});
