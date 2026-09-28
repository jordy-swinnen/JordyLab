import {
  createServiceFactory,
  SpectatorService,
} from '@ngneat/spectator/vitest';
import { AuthService } from './auth.service';
import { BiometricUnlockService } from './biometric-unlock.service';

const {
  isAvailable,
  isDataSaved,
  setData,
  deleteData,
  getSecureData,
} = vi.hoisted(() => ({
  isAvailable: vi.fn(),
  isDataSaved: vi.fn(),
  setData: vi.fn(),
  deleteData: vi.fn(),
  getSecureData: vi.fn(),
}));

vi.mock('@capgo/capacitor-native-biometric', () => ({
  AccessControl: { NONE: 0, BIOMETRY_CURRENT_SET: 1, BIOMETRY_ANY: 2 },
  NativeBiometric: { isAvailable, isDataSaved, setData, deleteData, getSecureData },
}));

describe('BiometricUnlockService', () => {
  let spectator: SpectatorService<BiometricUnlockService>;
  const getRefreshToken = vi.fn();
  const unlockWithRefreshToken = vi.fn();

  const createService = createServiceFactory({
    service: BiometricUnlockService,
    providers: [
      { provide: AuthService, useValue: { getRefreshToken, unlockWithRefreshToken } },
    ],
  });

  beforeEach(() => {
    vi.clearAllMocks();
    spectator = createService();
  });

  describe('isAvailable', () => {
    it('returns true when the plugin reports biometrics available', async () => {
      isAvailable.mockResolvedValueOnce({ isAvailable: true });

      expect(await spectator.service.isAvailable()).toBe(true);
    });

    it('returns false when the plugin call throws', async () => {
      isAvailable.mockRejectedValueOnce(new Error('no hardware'));

      expect(await spectator.service.isAvailable()).toBe(false);
    });
  });

  describe('refresh', () => {
    it('populates the available/enabled signals from the plugin', async () => {
      isAvailable.mockResolvedValueOnce({ isAvailable: true });
      isDataSaved.mockResolvedValueOnce({ isSaved: true });

      await spectator.service.refresh();

      expect(spectator.service.available()).toBe(true);
      expect(spectator.service.enabled()).toBe(true);
    });

    it('leaves the signals false when biometrics are unavailable and nothing is stored', async () => {
      isAvailable.mockResolvedValueOnce({ isAvailable: false });
      isDataSaved.mockResolvedValueOnce({ isSaved: false });

      await spectator.service.refresh();

      expect(spectator.service.available()).toBe(false);
      expect(spectator.service.enabled()).toBe(false);
    });
  });

  describe('enable', () => {
    it('stores the current refresh token behind biometric protection', async () => {
      getRefreshToken.mockReturnValue('the-refresh-token');
      setData.mockResolvedValueOnce(undefined);

      const succeeded = await spectator.service.enable();

      expect(succeeded).toBe(true);
      expect(setData).toHaveBeenCalledWith(
        expect.objectContaining({ value: 'the-refresh-token', accessControl: 2 }),
      );
      expect(spectator.service.enabled()).toBe(true);
    });

    it('fails without calling the plugin when there is no refresh token on the session', async () => {
      getRefreshToken.mockReturnValue(null);

      const succeeded = await spectator.service.enable();

      expect(succeeded).toBe(false);
      expect(setData).not.toHaveBeenCalled();
    });

    it('fails when the biometric prompt is cancelled', async () => {
      getRefreshToken.mockReturnValue('the-refresh-token');
      setData.mockRejectedValueOnce(new Error('User canceled'));

      expect(await spectator.service.enable()).toBe(false);
    });
  });

  describe('disable', () => {
    it('wipes the stored credential', async () => {
      deleteData.mockResolvedValueOnce(undefined);

      await spectator.service.disable();

      expect(deleteData).toHaveBeenCalledWith({ key: expect.any(String) });
      expect(spectator.service.enabled()).toBe(false);
    });

    it('does not throw when nothing was stored', async () => {
      deleteData.mockRejectedValueOnce(new Error('nothing stored'));

      await expect(spectator.service.disable()).resolves.toBeUndefined();
    });
  });

  describe('unlock', () => {
    it('exchanges the recovered refresh token for a fresh session on a successful biometric check', async () => {
      getSecureData.mockResolvedValueOnce({ value: 'recovered-refresh-token' });
      unlockWithRefreshToken.mockResolvedValueOnce(true);

      const succeeded = await spectator.service.unlock();

      expect(succeeded).toBe(true);
      expect(unlockWithRefreshToken).toHaveBeenCalledWith('recovered-refresh-token');
    });

    it('falls back to false, without calling the token exchange, when the biometric prompt fails', async () => {
      getSecureData.mockRejectedValueOnce(new Error('User canceled'));

      const succeeded = await spectator.service.unlock();

      expect(succeeded).toBe(false);
      expect(unlockWithRefreshToken).not.toHaveBeenCalled();
    });

    it('falls back to false when the recovered token is rejected by Keycloak (e.g. revoked)', async () => {
      getSecureData.mockResolvedValueOnce({ value: 'revoked-refresh-token' });
      unlockWithRefreshToken.mockResolvedValueOnce(false);

      expect(await spectator.service.unlock()).toBe(false);
    });
  });
});
