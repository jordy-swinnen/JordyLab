import { inject, Injectable } from '@angular/core';
import { AccessControl, NativeBiometric } from '@capgo/capacitor-native-biometric';
import { AuthService } from './auth.service';

const STORAGE_KEY = 'jordylab.offline-refresh-token';

/**
 * Opt-in biometric unlock backed by the `offline_access` refresh token, stored in the Android
 * Keystore behind biometric protection (spec US4, research D3). Any failure — cancelled prompt,
 * no biometrics enrolled, enrollment changed since storing, expired/revoked token — resolves to
 * `false`/no-op rather than throwing, so the caller always falls back to a normal login instead
 * of a silent retry loop (FR-012).
 */
@Injectable({ providedIn: 'root' })
export class BiometricUnlockService {
  readonly #auth = inject(AuthService);

  async isAvailable(): Promise<boolean> {
    try {
      const result = await NativeBiometric.isAvailable();

      return result.isAvailable;
    } catch {
      return false;
    }
  }

  async isEnabled(): Promise<boolean> {
    try {
      const result = await NativeBiometric.isDataSaved({ key: STORAGE_KEY });

      return result.isSaved;
    } catch {
      return false;
    }
  }

  /** Requires an active session with an `offline_access` refresh token (always requested, D2). */
  async enable(): Promise<boolean> {
    const refreshToken = this.#auth.getRefreshToken();
    if (!refreshToken) {
      console.error('Cannot enable biometric unlock: no refresh token on the current session');

      return false;
    }

    try {
      await NativeBiometric.setData({
        key: STORAGE_KEY,
        value: refreshToken,
        accessControl: AccessControl.BIOMETRY_ANY,
        title: 'Enable fingerprint unlock',
      });

      return true;
    } catch (error) {
      console.error('Enabling biometric unlock failed', error);

      return false;
    }
  }

  /** Wipes the stored credential — called explicitly on toggle-off and on logout (FR-012/FR-013), never left implicit. */
  async disable(): Promise<void> {
    try {
      await NativeBiometric.deleteData({ key: STORAGE_KEY });
    } catch {
      // Nothing was stored — already the desired end state.
    }
  }

  /**
   * Triggers the biometric prompt (via `getSecureData`'s Keystore-bound `CryptoObject`, no
   * separate `verifyIdentity()` call needed) and, on success, exchanges the recovered refresh
   * token for a fresh session. Returns `false` on any failure — cancelled, no biometrics, no
   * stored token (e.g. never enabled, or wiped by an admin revoke — D12), or a revoked/expired
   * token rejected by Keycloak.
   */
  async unlock(): Promise<boolean> {
    let refreshToken: string;
    try {
      const result = await NativeBiometric.getSecureData({
        key: STORAGE_KEY,
        reason: 'Unlock JordyLab',
      });
      refreshToken = result.value;
    } catch {
      return false;
    }

    return this.#auth.unlockWithRefreshToken(refreshToken);
  }
}
