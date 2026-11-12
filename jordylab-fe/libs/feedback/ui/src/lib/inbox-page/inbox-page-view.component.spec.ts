import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { anInboxTicketMock, InboxTicket } from '@jordylab-fe/feedback/api';
import { InboxPageViewComponent, TicketAcceptInput, TicketTextsInput } from './inbox-page-view.component';

describe('InboxPageViewComponent', () => {
  let spectator: Spectator<InboxPageViewComponent>;

  const createComponent = createComponentFactory({
    component: InboxPageViewComponent,
    detectChanges: false,
  });

  const render = (tickets: InboxTicket[], filterValue: 'NEW' | null = null): void => {
    spectator.setInput('tickets', tickets);
    spectator.setInput('loading', false);
    spectator.setInput('error', null);
    spectator.setInput('actioningId', null);
    spectator.setInput('filter', filterValue);
    spectator.detectChanges();
  };

  beforeEach(() => {
    spectator = createComponent();
    render([anInboxTicketMock()]);
  });

  it('shows sender, app, type and state for a ticket', () => {
    const ticket = spectator.query('[data-testid="inbox-ticket"]');

    expect(ticket?.textContent).toContain('Ada Palmer');
    expect(ticket?.textContent).toContain('Game Catalog');
    expect(ticket?.textContent).toContain('Bug report');
    expect(spectator.query('[data-testid="inbox-ticket-state"]')?.textContent).toContain('New');
  });

  it('offers Backlog, Reject and Accept on a NEW ticket', () => {
    expect(spectator.query('[data-testid="inbox-backlog"]')).toBeTruthy();
    expect(spectator.query('[data-testid="inbox-reject"]')).toBeTruthy();
    expect(spectator.query('[data-testid="inbox-accept"]')).toBeTruthy();
  });

  it('offers no state actions on a terminal ticket', () => {
    render([anInboxTicketMock({ state: 'REJECTED' })]);

    expect(spectator.query('[data-testid="inbox-backlog"]')).toBeFalsy();
    expect(spectator.query('[data-testid="inbox-reject"]')).toBeFalsy();
    expect(spectator.query('[data-testid="inbox-accept"]')).toBeFalsy();
    expect(spectator.query('[data-testid="inbox-texts-toggle"]')).toBeTruthy();
  });

  it('emits Accept with the optional private note', () => {
    const emitted: TicketAcceptInput[] = [];
    spectator.component.accept.subscribe((input) => emitted.push(input));

    spectator.click('[data-testid="inbox-accept"]');
    spectator.typeInElement("Repro'd on CachyOS too.", '[data-testid="inbox-accept-note"]');
    spectator.click('[data-testid="inbox-accept-confirm"]');

    expect(emitted).toEqual([{ id: anInboxTicketMock().id, privateNote: "Repro'd on CachyOS too." }]);
  });

  it('emits Accept without a note when the note is left empty', () => {
    const emitted: TicketAcceptInput[] = [];
    spectator.component.accept.subscribe((input) => emitted.push(input));

    spectator.click('[data-testid="inbox-accept"]');
    spectator.click('[data-testid="inbox-accept-confirm"]');

    expect(emitted).toEqual([{ id: anInboxTicketMock().id, privateNote: null }]);
  });

  it('emits Backlog and Reject for the ticket id', () => {
    const backlogged: string[] = [];
    const rejected: string[] = [];
    spectator.component.backlog.subscribe((id) => backlogged.push(id));
    spectator.component.reject.subscribe((id) => rejected.push(id));

    spectator.click('[data-testid="inbox-backlog"]');
    spectator.click('[data-testid="inbox-reject"]');

    expect(backlogged).toEqual([anInboxTicketMock().id]);
    expect(rejected).toEqual([anInboxTicketMock().id]);
  });

  it('prefills and emits both admin texts', () => {
    render([anInboxTicketMock({ reply: 'Old reply', privateNote: 'Old note' })]);
    const emitted: TicketTextsInput[] = [];
    spectator.component.saveTexts.subscribe((input) => emitted.push(input));

    spectator.click('[data-testid="inbox-texts-toggle"]');
    spectator.typeInElement('Thanks — accepted.', '[data-testid="inbox-reply"]');
    spectator.typeInElement('Fix is planned.', '[data-testid="inbox-private-note"]');
    spectator.click('[data-testid="inbox-save-texts"]');

    expect(emitted).toEqual([
      { id: anInboxTicketMock().id, reply: 'Thanks — accepted.', privateNote: 'Fix is planned.' },
    ]);
  });

  it('shows the backlog link and the last accept error when present', () => {
    render([
      anInboxTicketMock({
        state: 'ACCEPTED',
        backlogTicketUrl: 'https://kaneo.jordylab.be/dashboard/workspace/ws-1/project/proj-1/board',
        acceptError: 'The backlog is unreachable',
      }),
    ]);

    expect(spectator.query('[data-testid="inbox-backlog-link"]')).toBeTruthy();
    expect(spectator.query('[data-testid="inbox-accept-error"]')?.textContent).toContain(
      'The backlog is unreachable'
    );
  });

  it('shows the empty state when the filter matches nothing', () => {
    render([]);

    expect(spectator.query('.text-muted-foreground')?.textContent).toContain('No tickets in this view.');
  });
});
