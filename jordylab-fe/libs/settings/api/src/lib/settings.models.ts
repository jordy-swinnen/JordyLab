export type UserStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REVOKED';

/** Where an effective guest chat limit comes from (spec 014 FR-009, FR-010). */
export type ChatLimitSource = 'PERSONAL' | 'DEFAULT_SAVED' | 'ENVIRONMENT';

/** The saved default guest limit, with its source (spec 014 FR-009). */
export interface GuestChatLimit {
  dailyLimit: number;
  source: ChatLimitSource;
  updatedAt: string | null;
  updatedBy: string | null;
}

/** A locked-out user's pending password reset, shown on their card (spec 019 US6). */
export interface PendingResetRequest {
  requestId: string;
  requestedAt: string;
}

/** One account as the identity API lists it (spec 019 contracts/identity-api.md). */
export interface AppUser {
  id: string;
  username: string;
  displayName: string | null;
  role: 'admin' | 'guest' | null;
  status: UserStatus;
  /** App keys such as `gamecatalog` (spec 014 per-app grants). */
  appGrants: string[];
  createdAt: string;
  pendingResetRequest: PendingResetRequest | null;
}

/** One web session of an account, from Spring Session (spec 019 US4). */
export interface UserSession {
  sessionId: string;
  startedAt: string;
  lastAccessedAt: string;
}
