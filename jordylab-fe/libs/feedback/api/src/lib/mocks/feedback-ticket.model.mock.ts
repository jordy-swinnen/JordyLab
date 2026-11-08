import { FeedbackTicket, InboxTicket } from '../feedback.models';

export function aFeedbackTicketMock(overrides: Partial<FeedbackTicket> = {}): FeedbackTicket {
  return {
    id: '0c1e6f04-8d54-4a4b-9d3f-1e2a5b7c9d10',
    app: 'Game Catalog',
    type: 'Bug report',
    title: 'Grid loses my scroll position',
    description: 'When I open a game and go back, the grid jumps to the top.',
    state: 'NEW',
    reply: null,
    createdAt: '2026-11-02T18:40:12Z',
    ...overrides,
  };
}

export function anInboxTicketMock(overrides: Partial<InboxTicket> = {}): InboxTicket {
  return {
    ...aFeedbackTicketMock(),
    privateNote: null,
    sender: 'Ada Palmer',
    backlogTicketUrl: null,
    acceptError: null,
    ...overrides,
  };
}
