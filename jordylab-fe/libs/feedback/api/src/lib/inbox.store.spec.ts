import { HttpErrorResponse } from '@angular/common/http';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { InboxApiService } from './inbox-api.service';
import { InboxStore } from './inbox.store';
import { InboxTicket } from './feedback.models';
import { anInboxTicketMock } from './mocks/feedback-ticket.model.mock';

describe('InboxStore', () => {
  let spectator: SpectatorService<InboxStore>;
  const getInbox = vi.fn<InboxApiService['getInbox']>();
  const moveToBacklog = vi.fn<InboxApiService['moveToBacklog']>();
  const reject = vi.fn<InboxApiService['reject']>();
  const accept = vi.fn<InboxApiService['accept']>();
  const updateTexts = vi.fn<InboxApiService['updateTexts']>();

  const createService = createServiceFactory({
    service: InboxStore,
    providers: [
      {
        provide: InboxApiService,
        useValue: { getInbox, moveToBacklog, reject, accept, updateTexts },
      },
    ],
  });

  beforeEach(() => {
    getInbox.mockReset();
    getInbox.mockReturnValue(of([anInboxTicketMock()]));
    moveToBacklog.mockReset();
    reject.mockReset();
    accept.mockReset();
    updateTexts.mockReset();
  });

  it('loads the inbox on construction', () => {
    spectator = createService();

    expect(spectator.service.tickets()).toEqual([anInboxTicketMock()]);
    expect(spectator.service.loading()).toBe(false);
    expect(spectator.service.newCount()).toBe(1);
  });

  it('reloads with the chosen state filter', () => {
    spectator = createService();
    getInbox.mockReturnValue(of([anInboxTicketMock({ state: 'BACKLOG' })]));

    spectator.service.setFilter('BACKLOG');

    expect(getInbox).toHaveBeenLastCalledWith('BACKLOG');
    expect(spectator.service.filter()).toBe('BACKLOG');
    expect(spectator.service.tickets()[0].state).toBe('BACKLOG');
  });

  it('backlogs a ticket and reloads', () => {
    spectator = createService();
    moveToBacklog.mockReturnValue(of(anInboxTicketMock({ state: 'BACKLOG' })));

    spectator.service.moveToBacklog('ticket-1');

    expect(moveToBacklog).toHaveBeenCalledWith('ticket-1');
    expect(spectator.service.actioningId()).toBeNull();
  });

  it('accepts a ticket with the optional private note', () => {
    spectator = createService();
    accept.mockReturnValue(of(anInboxTicketMock({ state: 'ACCEPTED' })));

    spectator.service.accept('ticket-1', 'Repro on CachyOS too.');

    expect(accept).toHaveBeenCalledWith('ticket-1', 'Repro on CachyOS too.');
  });

  it('surfaces the Linear-unavailable reason and keeps the ticket list', () => {
    spectator = createService();
    accept.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503, error: { reason: 'LINEAR_UNAVAILABLE' } }))
    );

    spectator.service.accept('ticket-1', null);

    expect(spectator.service.error()).toBe(
      'Linear is unreachable right now — the ticket stays as it was, so you can retry.'
    );
    expect(spectator.service.tickets().length).toBe(1);
    expect(spectator.service.actioningId()).toBeNull();
  });

  it('surfaces the invalid-state reason', () => {
    spectator = createService();
    reject.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { reason: 'INVALID_STATE' } }))
    );

    spectator.service.reject('ticket-1');

    expect(spectator.service.error()).toBe('That ticket cannot be changed in its current state.');
  });

  it('saves both admin texts', () => {
    spectator = createService();
    updateTexts.mockReturnValue(of(anInboxTicketMock({ reply: 'Thanks!', privateNote: 'Fix planned.' })));

    spectator.service.updateTexts('ticket-1', 'Thanks!', 'Fix planned.');

    expect(updateTexts).toHaveBeenCalledWith('ticket-1', 'Thanks!', 'Fix planned.');
  });

  it('does not start a second action while one is in flight', () => {
    spectator = createService();
    reject.mockReturnValue(new Subject<InboxTicket>());

    spectator.service.reject('ticket-1');
    spectator.service.reject('ticket-2');

    expect(reject).toHaveBeenCalledTimes(1);
  });
});
