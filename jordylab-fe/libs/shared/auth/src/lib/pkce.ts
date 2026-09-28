const RANDOM_BYTE_LENGTH = 32;

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }

  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function randomBytes(): Uint8Array {
  return crypto.getRandomValues(new Uint8Array(RANDOM_BYTE_LENGTH));
}

/** A PKCE code verifier / `state` / `nonce` value — all three need the same random-token shape. */
export function randomToken(): string {
  return base64UrlEncode(randomBytes());
}

/** RFC 7636 `code_challenge` (S256) for a given `code_verifier`. */
export async function codeChallengeFromVerifier(codeVerifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(codeVerifier));

  return base64UrlEncode(new Uint8Array(digest));
}

/**
 * Decodes a JWT's payload without verifying its signature — the token just came from Keycloak's
 * own token endpoint over HTTPS (the native login's manual code exchange, `AuthService`), so
 * signature verification would only be checking the server against itself.
 */
export function decodeJwtPayload(token: string): Record<string, unknown> {
  const payload = token.split('.')[1] ?? '';
  const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');

  return JSON.parse(atob(padded)) as Record<string, unknown>;
}
