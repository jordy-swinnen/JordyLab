/**
 * Wire types for the feedback idea box (contracts/rest-api.md). The guest view and the admin view
 * share the ticket shape; the admin view adds the sender, the private note, the backlog link and the
 * last accept error — fields a guest response never carries.
 */
export type FeedbackAppId = 'GAME_CATALOG';

export type FeedbackTypeId = 'BUG_REPORT' | 'FEATURE_REQUEST' | 'IMPROVEMENT' | 'IDEA';

export type FeedbackState = 'NEW' | 'ACCEPTED' | 'BACKLOG' | 'REJECTED';

export interface FeedbackApp {
  id: FeedbackAppId;
  name: string;
}

export interface FeedbackTicket {
  id: string;
  app: string;
  type: string;
  title: string;
  description: string;
  state: FeedbackState;
  reply: string | null;
  createdAt: string;
}

export interface InboxTicket extends FeedbackTicket {
  privateNote: string | null;
  sender: string | null;
  backlogTicketUrl: string | null;
  acceptError: string | null;
}

export interface TicketSubmission {
  app: FeedbackAppId;
  type: FeedbackTypeId;
  title: string;
  description: string;
}

export const TITLE_MAX_LENGTH = 120;
export const DESCRIPTION_MAX_LENGTH = 5000;

export const FEEDBACK_TYPES: readonly { id: FeedbackTypeId; label: string }[] = [
  { id: 'BUG_REPORT', label: 'Bug report' },
  { id: 'FEATURE_REQUEST', label: 'Feature request' },
  { id: 'IMPROVEMENT', label: 'Improvement' },
  { id: 'IDEA', label: 'Idea' },
];

export const FEEDBACK_STATE_LABELS: Record<FeedbackState, string> = {
  NEW: 'New',
  ACCEPTED: 'Accepted',
  BACKLOG: 'Backlog',
  REJECTED: 'Rejected',
};
