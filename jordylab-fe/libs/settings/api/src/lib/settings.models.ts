export type UserStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** Where an effective guest chat limit comes from (spec 014 FR-009, FR-010). */
export type ChatLimitSource = 'PERSONAL' | 'DEFAULT_SAVED' | 'ENVIRONMENT';

/** One app in the admin's registry, with this person's grant state. */
export interface AppAccess {
  key: string;
  name: string;
  granted: boolean;
  grantable: boolean;
  /** Why the app cannot be granted; null when it can. */
  reason: string | null;
}

export interface PersonChatLimit {
  limit: number;
  source: ChatLimitSource;
}

export type SessionKind = 'WEB' | 'PHONE';

/** One active sign-in (spec 014 US5). Offline means the phone app's biometric-unlock session. */
export interface UserSession {
  id: string;
  kind: SessionKind;
  offline: boolean;
  startedAt: string;
  lastAccessedAt: string;
}

/** The saved default guest limit, with its source (spec 014 FR-009). */
export interface GuestChatLimit {
  dailyLimit: number;
  source: ChatLimitSource;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface AppUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  createdAt: string;
  enabled: boolean;
  realmRoles: string[];
  status: UserStatus;
  isAdmin: boolean;
  apps: AppAccess[];
  /** Null for the admin, who is never limited. */
  chatLimit: PersonChatLimit | null;
  /** Null when the identity provider could not answer for this person. */
  sessionCount: number | null;
}
