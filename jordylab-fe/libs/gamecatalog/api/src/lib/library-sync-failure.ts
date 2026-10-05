import { HttpErrorResponse } from '@angular/common/http';

const FAMILY_MESSAGES: Record<string, string> = {
  TOKEN_EXPIRED:
    'Steam rejected the token. It lasts about 24 hours and only comes from a browser that is signed in to store.steampowered.com — get a fresh one and try again.',
  NO_FAMILY_GROUP: 'This Steam account is not in a Steam Family group, so there is no shared library to read.',
  UNKNOWN_RESPONSE:
    "Steam's family service answered with something JordyLab cannot read. Try again later; if it keeps happening Steam may have changed the service.",
  FAMILY_TOKEN_REQUIRED: 'Paste a Steam token first.',
};

/** Turns a failed library sync into a sentence that says what to do, using the reason the backend reports. */
export function librarySyncFailureMessage(source: 'OWNED' | 'FAMILY', error: unknown): string {
  const body = error instanceof HttpErrorResponse ? (error.error as { reason?: string; errorCode?: string } | null) : null;
  if (source === 'OWNED' && body?.reason === 'STEAM_NOT_CONFIGURED') {
    return 'The Steam account is not configured on the server (STEAM_WEB_API_KEY and STEAM_ID).';
  }
  const specific = source === 'FAMILY' ? FAMILY_MESSAGES[body?.errorCode ?? body?.reason ?? ''] : undefined;

  return specific ?? `Failed to sync the ${source.toLowerCase()} library.`;
}
