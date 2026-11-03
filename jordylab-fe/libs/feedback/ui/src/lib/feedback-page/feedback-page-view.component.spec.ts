import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aFeedbackAppMock, aFeedbackTicketMock } from '@jordylab-fe/feedback/api';
import { FeedbackPageViewComponent } from './feedback-page-view.component';

describe('FeedbackPageViewComponent', () => {
  let spectator: Spectator<FeedbackPageViewComponent>;

  const createComponent = createComponentFactory({
    component: FeedbackPageViewComponent,
    detectChanges: false,
  });

  beforeEach(() => {
    spectator = createComponent();
    spectator.setInput('apps', [aFeedbackAppMock()]);
    spectator.setInput('tickets', [aFeedbackTicketMock()]);
    spectator.setInput('loading', false);
    spectator.setInput('submitting', false);
    spectator.setInput('error', null);
    spectator.detectChanges();
  });

  it('offers the allowed apps and the four types', () => {
    const appOptions = spectator.queryAll('[data-testid="feedback-app"] option');
    const typeOptions = spectator.queryAll('[data-testid="feedback-type"] option');

    expect(appOptions.length).toBe(2); // placeholder + Game Catalog
    expect(typeOptions.map((option) => option.textContent?.trim())).toEqual([
      'Bug report',
      'Feature request',
      'Improvement',
      'Idea',
    ]);
  });

  it('keeps submit disabled until app, title and description are filled', () => {
    const submitButton = spectator.query('[data-testid="feedback-submit"]') as HTMLButtonElement;

    expect(submitButton.disabled).toBe(true);

    spectator.selectOption('[data-testid="feedback-app"]', 'GAME_CATALOG');
    spectator.typeInElement('Grid loses my scroll position', '[data-testid="feedback-title"]');
    spectator.typeInElement('It jumps to the top.', '[data-testid="feedback-description"]');
    spectator.detectChanges();

    expect((spectator.query('[data-testid="feedback-submit"]') as HTMLButtonElement).disabled).toBe(false);
  });

  it('emits the submission and clears the form', () => {
    const emit = vi.fn();
    spectator.component.submitTicket.subscribe(emit);
    spectator.selectOption('[data-testid="feedback-app"]', 'GAME_CATALOG');
    spectator.typeInElement('Grid loses my scroll position', '[data-testid="feedback-title"]');
    spectator.typeInElement('It jumps to the top.', '[data-testid="feedback-description"]');
    spectator.click('[data-testid="feedback-submit"]');

    expect(emit).toHaveBeenCalledWith({
      app: 'GAME_CATALOG',
      type: 'BUG_REPORT',
      title: 'Grid loses my scroll position',
      description: 'It jumps to the top.',
    });
    expect((spectator.query('[data-testid="feedback-title"]') as HTMLInputElement).value).toBe('');
  });

  it('shows the state badge and the admin reply on a ticket', () => {
    spectator.setInput('tickets', [
      aFeedbackTicketMock({ state: 'ACCEPTED', reply: 'Thanks — fix is planned.' }),
    ]);
    spectator.detectChanges();

    expect(spectator.query('[data-testid="guest-ticket-state"]')?.textContent).toContain('Accepted');
    expect(spectator.query('[data-testid="guest-ticket-reply"]')?.textContent).toContain(
      'Thanks — fix is planned.'
    );
  });

  it('shows the empty state when there are no tickets', () => {
    spectator.setInput('tickets', []);
    spectator.detectChanges();

    expect(spectator.query('.text-muted-foreground')?.textContent).toContain(
      'You have not sent any feedback yet.'
    );
  });

  it('shows skeleton rows while loading', () => {
    spectator.setInput('loading', true);
    spectator.detectChanges();

    expect(spectator.query('lib-skeleton-rows')).toBeTruthy();
  });
});
