import { describe, expect, it } from 'vitest';
import { resolveApiUrl } from './resolve-api-url';

describe('resolveApiUrl', () => {
  it('is a no-op on web (isNative=false) even for an /api/ URL', () => {
    expect(resolveApiUrl('/api/gamecatalog/games', false, 'https://jordylab.example')).toBe(
      '/api/gamecatalog/games',
    );
  });

  it('is a no-op for a non-/api/ URL even when native', () => {
    expect(resolveApiUrl('/silent-check-sso.html', true, 'https://jordylab.example')).toBe(
      '/silent-check-sso.html',
    );
  });

  it('is a no-op for an already-absolute URL even when native', () => {
    expect(resolveApiUrl('https://other-host.example/api/x', true, 'https://jordylab.example')).toBe(
      'https://other-host.example/api/x',
    );
  });

  it('prefixes a relative /api/ URL with the base URL when native', () => {
    expect(resolveApiUrl('/api/gamecatalog/games', true, 'https://jordylab.example')).toBe(
      'https://jordylab.example/api/gamecatalog/games',
    );
  });

  it('does not produce a double slash when the base URL has a trailing slash', () => {
    expect(resolveApiUrl('/api/gamecatalog/games', true, 'https://jordylab.example/')).toBe(
      'https://jordylab.example/api/gamecatalog/games',
    );
  });
});
