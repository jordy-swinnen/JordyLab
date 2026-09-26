import { ScanSource } from '../gamecatalog.models';

export function aScanSourceMock(overrides: Partial<ScanSource> = {}): ScanSource {
  return {
    id: '2c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
    sourceKey: 'jordybox:STEAM',
    hostname: 'jordybox',
    sourceType: 'STEAM',
    platform: 'Steam',
    enabled: true,
    lastAttemptAt: '2026-08-02T10:20:00Z',
    lastSuccessAt: '2026-08-02T10:20:00Z',
    lastCheckedAt: '2026-08-02T10:30:00Z',
    lastOutcome: 'APPLIED',
    installedGameCount: 412,
    ...overrides,
  };
}
