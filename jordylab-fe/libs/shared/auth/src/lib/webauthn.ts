/**
 * Browser-side WebAuthn plumbing for Spring Security's passkey endpoints (spec 019 US2, D16). The server exchanges
 * standard WebAuthn JSON with binary fields base64url-encoded; these helpers convert to and from the ArrayBuffers
 * `navigator.credentials` expects, and serialize the results back.
 */

export interface PublicKeyCredentialDescriptorJson {
  id: string;
  type: string;
  transports?: string[];
}

export interface PublicKeyCredentialRequestOptionsJson {
  challenge: string;
  timeout?: number;
  rpId?: string;
  allowCredentials?: PublicKeyCredentialDescriptorJson[];
  userVerification?: UserVerificationRequirement;
}

export interface PublicKeyCredentialCreationOptionsJson {
  rp: { id: string; name: string };
  user: { id: string; name: string; displayName: string };
  challenge: string;
  pubKeyCredParams: PublicKeyCredentialParameters[];
  timeout?: number;
  excludeCredentials?: PublicKeyCredentialDescriptorJson[];
  authenticatorSelection?: AuthenticatorSelectionCriteria;
  attestation?: AttestationConveyancePreference;
}

export function base64UrlToBuffer(value: string): ArrayBuffer {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(padded.padEnd(Math.ceil(padded.length / 4) * 4, '='));
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index++) {
    bytes[index] = binary.charCodeAt(index);
  }

  return bytes.buffer;
}

export function bufferToBase64Url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }

  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function toRequestOptions(json: PublicKeyCredentialRequestOptionsJson): PublicKeyCredentialRequestOptions {
  return {
    challenge: base64UrlToBuffer(json.challenge),
    ...(json.rpId ? { rpId: json.rpId } : {}),
    ...(json.timeout ? { timeout: json.timeout } : {}),
    ...(json.userVerification ? { userVerification: json.userVerification } : {}),
    allowCredentials: (json.allowCredentials ?? []).map((descriptor) => ({
      id: base64UrlToBuffer(descriptor.id),
      type: descriptor.type as PublicKeyCredentialType,
      ...(descriptor.transports ? { transports: descriptor.transports as AuthenticatorTransport[] } : {}),
    })),
  };
}

export function toCreationOptions(json: PublicKeyCredentialCreationOptionsJson): PublicKeyCredentialCreationOptions {
  return {
    rp: json.rp,
    user: {
      id: base64UrlToBuffer(json.user.id),
      name: json.user.name,
      displayName: json.user.displayName,
    },
    challenge: base64UrlToBuffer(json.challenge),
    pubKeyCredParams: json.pubKeyCredParams,
    ...(json.timeout ? { timeout: json.timeout } : {}),
    ...(json.attestation ? { attestation: json.attestation } : {}),
    ...(json.authenticatorSelection ? { authenticatorSelection: json.authenticatorSelection } : {}),
    excludeCredentials: (json.excludeCredentials ?? []).map((descriptor) => ({
      id: base64UrlToBuffer(descriptor.id),
      type: descriptor.type as PublicKeyCredentialType,
      ...(descriptor.transports ? { transports: descriptor.transports as AuthenticatorTransport[] } : {}),
    })),
  };
}

export function canUsePasskeys(): boolean {
  return typeof window !== 'undefined' && typeof window.PublicKeyCredential === 'function';
}

/** Runs the fingerprint/face prompt for a sign-in assertion; resolves null when the user cancels. */
export async function getAssertion(options: PublicKeyCredentialRequestOptions): Promise<PublicKeyCredential | null> {
  try {
    return (await navigator.credentials.get({ publicKey: options })) as PublicKeyCredential | null;
  } catch (error) {
    if (error instanceof DOMException && error.name === 'NotAllowedError') {
      return null;
    }

    throw error;
  }
}

/** Runs the fingerprint/face prompt for a new credential; resolves null when the user cancels. */
export async function createCredential(
  options: PublicKeyCredentialCreationOptions,
): Promise<PublicKeyCredential | null> {
  try {
    return (await navigator.credentials.create({ publicKey: options })) as PublicKeyCredential | null;
  } catch (error) {
    if (error instanceof DOMException && error.name === 'NotAllowedError') {
      return null;
    }

    throw error;
  }
}

/** The JSON Spring Security's `/login/webauthn` endpoint expects for an assertion. */
export function serializeAssertion(credential: PublicKeyCredential): Record<string, unknown> {
  const response = credential.response as AuthenticatorAssertionResponse;

  return {
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type,
    response: {
      authenticatorData: bufferToBase64Url(response.authenticatorData),
      clientDataJSON: bufferToBase64Url(response.clientDataJSON),
      signature: bufferToBase64Url(response.signature),
      userHandle: response.userHandle ? bufferToBase64Url(response.userHandle) : null,
    },
    clientExtensionResults: credential.getClientExtensionResults(),
    ...(credential.authenticatorAttachment ? { authenticatorAttachment: credential.authenticatorAttachment } : {}),
  };
}

/** The JSON Spring Security's `/webauthn/register` endpoint expects for a new credential. */
export function serializeAttestation(credential: PublicKeyCredential): Record<string, unknown> {
  const response = credential.response as AuthenticatorAttestationResponse;

  return {
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type,
    response: {
      attestationObject: bufferToBase64Url(response.attestationObject),
      clientDataJSON: bufferToBase64Url(response.clientDataJSON),
      transports: typeof response.getTransports === 'function' ? response.getTransports() : [],
    },
    clientExtensionResults: credential.getClientExtensionResults(),
    ...(credential.authenticatorAttachment ? { authenticatorAttachment: credential.authenticatorAttachment } : {}),
  };
}
