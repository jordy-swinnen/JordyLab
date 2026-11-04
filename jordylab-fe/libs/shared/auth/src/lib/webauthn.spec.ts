import {
  base64UrlToBuffer,
  bufferToBase64Url,
  serializeAssertion,
  serializeAttestation,
  toCreationOptions,
  toRequestOptions,
} from './webauthn';

describe('webauthn helpers', () => {
  it('round-trips base64url binary values', () => {
    const bytes = new Uint8Array([0, 1, 2, 250, 251, 252, 253, 254, 255]);

    const encoded = bufferToBase64Url(bytes.buffer);
    const decoded = new Uint8Array(base64UrlToBuffer(encoded));

    expect(encoded).not.toContain('+');
    expect(encoded).not.toContain('/');
    expect(encoded).not.toContain('=');
    expect(Array.from(decoded)).toEqual(Array.from(bytes));
  });

  it('decodes request options into ArrayBuffers', () => {
    const options = toRequestOptions({
      challenge: bufferToBase64Url(new Uint8Array([1, 2, 3]).buffer),
      rpId: 'localhost',
      userVerification: 'preferred',
      allowCredentials: [
        {
          id: bufferToBase64Url(new Uint8Array([4, 5]).buffer),
          type: 'public-key',
          transports: ['internal'],
        },
      ],
    });

    expect(new Uint8Array(options.challenge as ArrayBuffer)).toEqual(new Uint8Array([1, 2, 3]));
    expect(options.rpId).toBe('localhost');
    expect(new Uint8Array(options.allowCredentials?.[0].id as ArrayBuffer)).toEqual(new Uint8Array([4, 5]));
  });

  it('decodes creation options, including the user handle', () => {
    const options = toCreationOptions({
      rp: { id: 'localhost', name: 'JordyLab' },
      user: {
        id: bufferToBase64Url(new Uint8Array([9, 9, 9]).buffer),
        name: 'jordy',
        displayName: 'Jordy',
      },
      challenge: bufferToBase64Url(new Uint8Array([7]).buffer),
      pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
      excludeCredentials: [],
    });

    expect(new Uint8Array(options.user.id as ArrayBuffer)).toEqual(new Uint8Array([9, 9, 9]));
    expect(options.user.name).toBe('jordy');
    expect(new Uint8Array(options.challenge as ArrayBuffer)).toEqual(new Uint8Array([7]));
  });

  it('serializes an assertion with base64url responses', () => {
    const credential = {
      id: 'cred-1',
      rawId: new Uint8Array([1, 2]).buffer,
      type: 'public-key',
      authenticatorAttachment: 'platform',
      getClientExtensionResults: () => ({ credProps: { rk: true } }),
      response: {
        authenticatorData: new Uint8Array([3]).buffer,
        clientDataJSON: new Uint8Array([4]).buffer,
        signature: new Uint8Array([5]).buffer,
        userHandle: new Uint8Array([6]).buffer,
      },
    } as unknown as PublicKeyCredential;

    const serialized = serializeAssertion(credential) as {
      id: string;
      rawId: string;
      response: Record<string, string>;
    };

    expect(serialized.id).toBe('cred-1');
    expect(serialized.rawId).toBe(bufferToBase64Url(new Uint8Array([1, 2]).buffer));
    expect(serialized.response['signature']).toBe(bufferToBase64Url(new Uint8Array([5]).buffer));
    expect(serialized.response['userHandle']).toBe(bufferToBase64Url(new Uint8Array([6]).buffer));
  });

  it('serializes an attestation with transports', () => {
    const credential = {
      id: 'cred-2',
      rawId: new Uint8Array([1]).buffer,
      type: 'public-key',
      authenticatorAttachment: 'platform',
      getClientExtensionResults: () => ({}),
      response: {
        attestationObject: new Uint8Array([8]).buffer,
        clientDataJSON: new Uint8Array([9]).buffer,
        getTransports: () => ['internal', 'hybrid'],
      },
    } as unknown as PublicKeyCredential;

    const serialized = serializeAttestation(credential) as {
      response: { attestationObject: string; transports: string[] };
    };

    expect(serialized.response.attestationObject).toBe(bufferToBase64Url(new Uint8Array([8]).buffer));
    expect(serialized.response.transports).toEqual(['internal', 'hybrid']);
  });
});
