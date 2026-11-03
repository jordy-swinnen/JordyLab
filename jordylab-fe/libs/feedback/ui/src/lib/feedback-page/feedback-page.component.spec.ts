import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import {
  aFeedbackAppMock,
  aFeedbackTicketMock,
  FeedbackStore,
  TicketSubmission,
} from '@jordylab-fe/feedback/api';
import { FeedbackPageComponent } from './feedback-page.component';

describe('FeedbackPageComponent', () => {
  const apps = signal([aFeedbackAppMock()]);
  const tickets = signal([aFeedbackTicketMock()]);
  const loading = signal(false);
  const submitting = signal(false);
  const error = signal<string | null>(null);
  const submit = vi.fn<FeedbackStore['submit']>();
  const load = vi.fn<FeedbackStore['load']>();
  const dismissError = vi.fn<FeedbackStore['dismissError']>();

  const storeMock = {
    apps: apps.asReadonly(),
    tickets: tickets.asReadonly(),
    loading: loading.asReadonly(),
    submitting: submitting.asReadonly(),
    error: error.asReadonly(),
    submit,
    load,
    dismissError,
  };

  let spectator: Spectator<FeedbackPageComponent>;

  const createComponent = createComponentFactory({
    component: FeedbackPageComponent,
    providers: [{ provide: FeedbackStore, useValue: storeMock }],
  });

  beforeEach(() => {
    apps.set([aFeedbackAppMock()]);
    tickets.set([aFeedbackTicketMock()]);
    loading.set(false);
    submitting.set(false);
    error.set(null);
    submit.mockReset();
    load.mockReset();
    dismissError.mockReset();
    spectator = createComponent();
  });

  it('renders the ticket list from the store', () => {
    expect(spectator.queryAll('[data-testid="guest-ticket"]').length).toBe(1);
    expect(spectator.query('[data-testid="guest-ticket-state"]')?.textContent).toContain('New');
  });

  it('forwards a submission to the store', () => {
    const view = spectator.query('lib-feedback-page-view');

    expect(view).toBeTruthy();
  });

  it('shows the error state and forwards dismissal', () => {
    error.set('Something went wrong.');
    spectator.detectChanges();

    expect(spectator.query('lib-error-state')?.textContent).toContain('Something went wrong.');

    spectator.click('lib-error-state button');
    expect(dismissError).toHaveBeenCalled();
  });

  it('renders a submission event from the view into the store', () => {
    const submission: TicketSubmission = {
      app: 'GAME_CATALOG',
      type: 'BUG_REPORT',
      title: 'Title',
      description: 'Description',
    };

    spectator.triggerEventHandler('lib-feedback-page-view', 'submitTicket', submission);

    expect(submit).toHaveBeenCalledWith(submission);
  });
});
