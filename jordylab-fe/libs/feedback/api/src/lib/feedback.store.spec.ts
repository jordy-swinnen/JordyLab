import { HttpErrorResponse } from '@angular/common/http';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { FeedbackApiService } from './feedback-api.service';
import { FeedbackStore } from './feedback.store';
import { FeedbackTicket } from './feedback.models';
import { aFeedbackAppMock } from './mocks/feedback-app.model.mock';
import { aFeedbackTicketMock } from './mocks/feedback-ticket.model.mock';

describe('FeedbackStore', () => {
  let spectator: SpectatorService<FeedbackStore>;
  const getApps = vi.fn<FeedbackApiService['getApps']>();
  const getOwnTickets = vi.fn<FeedbackApiService['getOwnTickets']>();
  const submit = vi.fn<FeedbackApiService['submit']>();

  const createService = createServiceFactory({
    service: FeedbackStore,
    providers: [{ provide: FeedbackApiService, useValue: { getApps, getOwnTickets, submit } }],
  });

  beforeEach(() => {
    getApps.mockReset();
    getApps.mockReturnValue(of([aFeedbackAppMock()]));
    getOwnTickets.mockReset();
    getOwnTickets.mockReturnValue(of([aFeedbackTicketMock()]));
    submit.mockReset();
  });

  it('loads the apps and the sender tickets on construction', () => {
    spectator = createService();

    expect(spectator.service.apps()).toEqual([aFeedbackAppMock()]);
    expect(spectator.service.tickets()).toEqual([aFeedbackTicketMock()]);
    expect(spectator.service.loading()).toBe(false);
  });

  it('is loading until the tickets arrive', () => {
    const tickets = new Subject<FeedbackTicket[]>();
    getOwnTickets.mockReturnValue(tickets.asObservable());
    spectator = createService();

    expect(spectator.service.loading()).toBe(true);

    tickets.next([]);

    expect(spectator.service.loading()).toBe(false);
  });

  it('sets an error message when the tickets cannot load', () => {
    getOwnTickets.mockReturnValue(throwError(() => new Error('network error')));
    spectator = createService();

    expect(spectator.service.error()).toBe('Failed to load your feedback.');
    expect(spectator.service.tickets()).toEqual([]);
  });

  it('submits a ticket and reloads the list', () => {
    submit.mockReturnValue(of(aFeedbackTicketMock()));
    spectator = createService();
    getOwnTickets.mockReturnValue(of([aFeedbackTicketMock(), aFeedbackTicketMock({ id: 'new-ticket' })]));

    spectator.service.submit({
      app: 'GAME_CATALOG',
      type: 'BUG_REPORT',
      title: 'Grid loses my scroll position',
      description: 'It jumps to the top.',
    });

    expect(submit).toHaveBeenCalledWith({
      app: 'GAME_CATALOG',
      type: 'BUG_REPORT',
      title: 'Grid loses my scroll position',
      description: 'It jumps to the top.',
    });
    expect(spectator.service.tickets().length).toBe(2);
    expect(spectator.service.submitting()).toBe(false);
  });

  it('surfaces the daily limit reason as a friendly message', () => {
    submit.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 429, error: { reason: 'FEEDBACK_LIMIT_REACHED' } }))
    );
    spectator = createService();

    spectator.service.submit({
      app: 'GAME_CATALOG',
      type: 'IDEA',
      title: 'Title',
      description: 'Description',
    });

    expect(spectator.service.error()).toBe(
      'You have reached the daily limit of five feedback tickets. Try again tomorrow.'
    );
    expect(spectator.service.submitting()).toBe(false);
  });

  it('does not start a second submission while one is in flight', () => {
    submit.mockReturnValue(new Subject<FeedbackTicket>());
    spectator = createService();

    spectator.service.submit({ app: 'GAME_CATALOG', type: 'IDEA', title: 'One', description: 'One' });
    spectator.service.submit({ app: 'GAME_CATALOG', type: 'IDEA', title: 'Two', description: 'Two' });

    expect(submit).toHaveBeenCalledTimes(1);
  });
});
