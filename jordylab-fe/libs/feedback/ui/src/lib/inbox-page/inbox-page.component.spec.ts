import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { anInboxTicketMock, InboxStore } from '@jordylab-fe/feedback/api';
import { InboxPageComponent } from './inbox-page.component';

describe('InboxPageComponent', () => {
  const tickets = signal([anInboxTicketMock()]);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const actioningId = signal<string | null>(null);
  const filter = signal<'NEW' | 'ACCEPTED' | 'BACKLOG' | 'REJECTED' | null>(null);
  const setFilter = vi.fn<InboxStore['setFilter']>();
  const moveToBacklog = vi.fn<InboxStore['moveToBacklog']>();
  const reject = vi.fn<InboxStore['reject']>();
  const accept = vi.fn<InboxStore['accept']>();
  const updateTexts = vi.fn<InboxStore['updateTexts']>();
  const load = vi.fn<InboxStore['load']>();
  const dismissError = vi.fn<InboxStore['dismissError']>();

  const storeMock = {
    tickets: tickets.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    actioningId: actioningId.asReadonly(),
    filter: filter.asReadonly(),
    setFilter,
    moveToBacklog,
    reject,
    accept,
    updateTexts,
    load,
    dismissError,
  };

  let spectator: Spectator<InboxPageComponent>;

  const createComponent = createComponentFactory({
    component: InboxPageComponent,
    providers: [{ provide: InboxStore, useValue: storeMock }],
  });

  beforeEach(() => {
    tickets.set([anInboxTicketMock()]);
    loading.set(false);
    error.set(null);
    actioningId.set(null);
    filter.set(null);
    setFilter.mockReset();
    moveToBacklog.mockReset();
    reject.mockReset();
    accept.mockReset();
    updateTexts.mockReset();
    load.mockReset();
    dismissError.mockReset();
    spectator = createComponent();
  });

  it('renders the inbox from the store', () => {
    expect(spectator.queryAll('[data-testid="inbox-ticket"]').length).toBe(1);
    expect(spectator.query('[data-testid="inbox-ticket"]')?.textContent).toContain('Ada Palmer');
  });

  it('forwards the state filter to the store', () => {
    spectator.triggerEventHandler('lib-inbox-page-view', 'setFilter', 'BACKLOG');

    expect(setFilter).toHaveBeenCalledWith('BACKLOG');
  });

  it('forwards backlog, reject and accept to the store', () => {
    spectator.triggerEventHandler('lib-inbox-page-view', 'backlog', 'ticket-1');
    spectator.triggerEventHandler('lib-inbox-page-view', 'reject', 'ticket-2');
    spectator.triggerEventHandler('lib-inbox-page-view', 'accept', {
      id: 'ticket-3',
      privateNote: 'Repro on CachyOS too.',
    });

    expect(moveToBacklog).toHaveBeenCalledWith('ticket-1');
    expect(reject).toHaveBeenCalledWith('ticket-2');
    expect(accept).toHaveBeenCalledWith('ticket-3', 'Repro on CachyOS too.');
  });

  it('forwards the two admin texts to the store', () => {
    spectator.triggerEventHandler('lib-inbox-page-view', 'saveTexts', {
      id: 'ticket-1',
      reply: 'Thanks!',
      privateNote: 'Fix planned.',
    });

    expect(updateTexts).toHaveBeenCalledWith('ticket-1', 'Thanks!', 'Fix planned.');
  });
});
