import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, Signal, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  createCredential,
  getAssertion,
  serializeAssertion,
  serializeAttestation,
  toCreationOptions,
  toRequestOptions,
} from './webauthn';
import type {
  PublicKeyCredentialCreationOptionsJson,
  PublicKeyCredentialRequestOptionsJson,
} from './webauthn';

/** The two application roles (spec 019 FR-003). Self-registered users hold neither until approved. */
export type AppRole = 'admin' | 'guest';

/** Per-app grant authorities (spec 014), spelled as the backend authorities: `app-gamecatalog`. */
export type AccessRole = `app-${string}`;

export type UserStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REVOKED';

/** The signed-in user as the identity API returns it (spec 019 contracts/identity-api.md). */
export interface CurrentUser {
  id: string;
  username: string;
  displayName: string | null;
  role: AppRole | null;
  status: UserStatus;
  /** App keys such as `gamecatalog` (spec 014's per-app grants). */
  appGrants: string[];
}

const APP_ROLES: readonly AppRole[] = ['admin', 'guest'];

/** One enrolled passkey (spec 019 US2). */
export interface Passkey {
  credentialId: string;
  label: string | null;
  createdAt: string;
  lastUsedAt: string | null;
}

/**
 * Session authentication against the backend (spec 019): the browser holds an HttpOnly session cookie, so the app
 * sends no bearer header and CSRF rides on Angular's built-in XSRF interceptor. The user profile comes from
 * `GET /api/identity/me` on start-up; login, logout, password and display-name changes are ordinary API calls.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  #http = inject(HttpClient);
  #init: Promise<boolean> | null = null;
  #csrf: Promise<void> | null = null;
  #user = signal<CurrentUser | null>(null);

  readonly user = this.#user.asReadonly();
  readonly isAuthenticated = computed(() => this.#user() !== null);
  readonly username: Signal<string | null> = computed(() => this.#user()?.username ?? null);
  readonly displayName = computed(() => this.#user()?.displayName ?? null);
  /** The person's role plus their app-grant authorities (`app-gamecatalog`), matching the backend names. */
  readonly roles = computed<string[]>(() => {
    const user = this.#user();
    if (!user) {
      return [];
    }
    const grants = user.appGrants.map((grant) => `app-${grant}`);

    return user.role ? [user.role, ...grants] : grants;
  });
  readonly isAdmin = computed(() => this.#user()?.role === 'admin');
  readonly isGuest = computed(() => this.#user()?.role === 'guest');
  readonly hasAppRole = computed(() => APP_ROLES.some((role) => this.roles().includes(role)));
  /** Admin, or an approved person the admin granted the Game Catalog to (spec 014). */
  readonly hasGameCatalogAccess = computed(
    () => this.isAdmin() || (this.#user()?.appGrants ?? []).includes('gamecatalog'),
  );

  /**
   * Idempotent and concurrency-safe: route guards run in parallel and may call this simultaneously, so every caller
   * shares one in-flight `/me` request instead of one caller reading half-initialized state.
   */
  async init(): Promise<boolean> {
    this.#init ??= this.#loadCurrentUser();

    return this.#init;
  }

  async login(username: string, password: string): Promise<CurrentUser> {
    await this.#ensureCsrfToken();
    const user = await firstValueFrom(
      this.#http.post<CurrentUser>('/api/identity/auth/login', { username, password }),
    );
    this.#user.set(user);
    this.#init = Promise.resolve(true);

    return user;
  }

  /** Registers a pending account; the caller usually signs in right after to reach the awaiting-approval screen. */
  async register(username: string, password: string, displayName?: string): Promise<void> {
    await this.#ensureCsrfToken();
    await firstValueFrom(
      this.#http.post<{ id: string }>('/api/identity/auth/register', {
        username,
        password,
        displayName: displayName ?? null,
      }),
    );
  }

  async logout(): Promise<void> {
    await this.#ensureCsrfToken();
    await firstValueFrom(this.#http.post<void>('/api/identity/auth/logout', {}));
    this.#user.set(null);
    this.#init = Promise.resolve(false);
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await this.#ensureCsrfToken();
    await firstValueFrom(
      this.#http.post<void>('/api/identity/auth/change-password', { currentPassword, newPassword }),
    );
  }

  /**
   * A locked-out user asks the admin for a reset (spec 019 US6). The backend answers the same for unknown
   * usernames, so the caller shows one generic confirmation either way.
   */
  async requestPasswordReset(username: string, newPassword: string): Promise<void> {
    await this.#ensureCsrfToken();
    await firstValueFrom(
      this.#http.post<void>('/api/identity/auth/reset-requests', { username, newPassword }),
    );
  }

  async updateDisplayName(displayName: string): Promise<void> {
    await this.#ensureCsrfToken();
    await firstValueFrom(this.#http.patch<void>('/api/identity/me', { displayName }));
    const user = this.#user();
    if (user) {
      this.#user.set({ ...user, displayName });
    }
  }

  /** Re-reads the profile (for example after an admin approved the account in another tab). */
  async refresh(): Promise<boolean> {
    this.#init = this.#loadCurrentUser();

    return this.#init;
  }

  /** One enrolled passkey as the identity API lists it (spec 019 US2). */
  async passkeys(): Promise<Passkey[]> {
    const response = await firstValueFrom(
      this.#http.get<{ passkeys: Passkey[] }>('/api/identity/passkeys'),
    );

    return response.passkeys;
  }

  /**
   * Signs in with a passkey (spec 019 US2): the verification options are usernameless — the authenticator offers the
   * passkeys stored for this site — and the assertion establishes the same session a password would.
   */
  async signInWithPasskey(): Promise<CurrentUser> {
    await this.#ensureCsrfToken();
    const options = await firstValueFrom(
      this.#http.post<PublicKeyCredentialRequestOptionsJson>('/webauthn/authenticate/options', {}),
    );
    const credential = await getAssertion(toRequestOptions(options));
    if (!credential) {
      throw new Error('PASSKEY_CANCELLED');
    }
    await firstValueFrom(this.#http.post('/login/webauthn', serializeAssertion(credential)));
    const user = await firstValueFrom(this.#http.get<CurrentUser>('/api/identity/me'));
    this.#user.set(user);
    this.#init = Promise.resolve(true);

    return user;
  }

  /** Adds a passkey to the signed-in account; the device prompts for fingerprint/face. */
  async addPasskey(label: string): Promise<void> {
    await this.#ensureCsrfToken();
    const options = await firstValueFrom(
      this.#http.post<PublicKeyCredentialCreationOptionsJson>('/webauthn/register/options', {}),
    );
    const credential = await createCredential(toCreationOptions(options));
    if (!credential) {
      throw new Error('PASSKEY_CANCELLED');
    }
    await firstValueFrom(
      this.#http.post('/webauthn/register', {
        publicKey: { credential: serializeAttestation(credential), label },
      }),
    );
  }

  async removePasskey(credentialId: string): Promise<void> {
    await this.#ensureCsrfToken();
    await firstValueFrom(
      this.#http.delete<void>(`/api/identity/passkeys/${encodeURIComponent(credentialId)}`),
    );
  }

  async #loadCurrentUser(): Promise<boolean> {
    try {
      await this.#ensureCsrfToken();
      const user = await firstValueFrom(this.#http.get<CurrentUser>('/api/identity/me'));
      this.#user.set(user);

      return true;
    } catch {
      this.#user.set(null);

      return false;
    }
  }

  /**
   * The backend writes the `XSRF-TOKEN` cookie on this endpoint; Angular's default XSRF interceptor then sends it as
   * `X-XSRF-TOKEN` on every same-origin mutating request.
   */
  #ensureCsrfToken(): Promise<void> {
    this.#csrf ??= firstValueFrom(this.#http.get<void>('/api/identity/auth/csrf'))
      .then(() => undefined)
      .catch(() => undefined);

    return this.#csrf;
  }
}

/** A human message for a failed auth call, based on the backend's stable reason codes. */
export function authErrorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Something went wrong. Try again.';
  }
  const body = error.error as { reason?: string; message?: string } | null;
  switch (body?.reason) {
    case 'INVALID_CREDENTIALS':
      return 'Invalid username or password.';
    case 'ACCOUNT_LOCKED':
      return 'Too many failed attempts. Try again in a few minutes.';
    case 'ACCOUNT_REJECTED':
      return 'This account was rejected.';
    case 'ACCOUNT_REVOKED':
      return 'This account no longer has access.';
    case 'USERNAME_TAKEN':
      return 'That username is already taken.';
    case 'WEAK_PASSWORD':
    case 'INVALID_USERNAME':
      return body.message ?? 'Check the form and try again.';
    default:
      return 'Something went wrong. Try again.';
  }
}
