export type SteamTokenResult =
  | { readonly token: string }
  | { readonly problem: string };

const MIN_TOKEN_LENGTH = 40;

const NOT_SIGNED_IN =
  'That page shows an empty "data": you are not signed in to the Steam store in this browser. Sign in at store.steampowered.com, reload the page and copy the token again.';
const NOT_A_TOKEN =
  'That does not look like a Steam token. It is one long string without spaces (it starts with "eyJ").';

/**
 * Pulls the Steam token out of whatever was pasted: the bare token, or the whole page text of the Steam config page
 * ({"success":1,"data":{"webapi_token":"…"}}), which is what most people copy. Explains an empty page instead of sending
 * Steam a value it is certain to refuse.
 */
export function readSteamToken(pasted: string): SteamTokenResult {
  const text = pasted.trim();
  if (text.startsWith('{')) {
    return readFromJson(text);
  }

  return validated(
    text.replace(/^access_token=/i, '').replace(/^["']|["']$/g, ''),
  );
}

function readFromJson(text: string): SteamTokenResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { problem: NOT_A_TOKEN };
  }
  const root = (parsed ?? {}) as Record<string, unknown>;
  const data = root['data'];
  const candidates = [
    typeof data === 'object' && data !== null && !Array.isArray(data)
      ? (data as Record<string, unknown>)['webapi_token']
      : undefined,
    root['webapi_token'],
    root['token'],
  ];
  const token = candidates.find(
    (candidate): candidate is string =>
      typeof candidate === 'string' && candidate.trim().length > 0,
  );
  if (token) {
    return validated(token.trim());
  }

  return {
    problem:
      Array.isArray(data) || data === undefined ? NOT_SIGNED_IN : NOT_A_TOKEN,
  };
}

function validated(token: string): SteamTokenResult {
  return token.length >= MIN_TOKEN_LENGTH && !/\s/.test(token)
    ? { token }
    : { problem: NOT_A_TOKEN };
}
