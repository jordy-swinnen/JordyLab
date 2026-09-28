import { codeChallengeFromVerifier, decodeJwtPayload, randomToken } from './pkce';

describe('pkce', () => {
  describe('randomToken', () => {
    it('produces URL-safe tokens with no padding', () => {
      const token = randomToken();

      expect(token).toMatch(/^[A-Za-z0-9_-]+$/);
    });

    it('produces a different token on every call', () => {
      expect(randomToken()).not.toBe(randomToken());
    });
  });

  describe('codeChallengeFromVerifier', () => {
    it('matches the RFC 7636 S256 test vector', async () => {
      // From RFC 7636 Appendix B.
      const verifier = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';

      const challenge = await codeChallengeFromVerifier(verifier);

      expect(challenge).toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
    });
  });

  describe('decodeJwtPayload', () => {
    it('decodes a base64url JWT payload', () => {
      const payload = { sub: 'user-1', nonce: 'abc123' };
      const encoded = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
      const token = `header.${encoded}.signature`;

      expect(decodeJwtPayload(token)).toEqual(payload);
    });
  });
});
