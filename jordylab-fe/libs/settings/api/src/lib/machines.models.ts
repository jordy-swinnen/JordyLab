export interface Machine {
  id: string;
  name: string;
  scope: string;
  createdAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
}

/** The one-time creation response: the token is never shown again (spec 019 US5). */
export interface CreatedMachine {
  id: string;
  name: string;
  token: string;
}
