import { ScanSource } from '../gamecatalog.models';
import { aPlatformChipMock } from './platform-chip.model.mock';

export function aScanSourceMock(overrides: Partial<ScanSource> = {}): ScanSource {
  return {
    id: '2c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
    sourceKey: 'jordybox:STEAM',
    hostId: '4c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
    hostname: 'jordybox',
    displayName: null,
    label: 'jordybox',
    sourceType: 'STEAM',
    platform: 'Steam',
    platformChip: aPlatformChipMock({ name: 'Steam', family: 'STEAM', background: '#1B2838', foreground: '#66C0F4', border: '#66C0F4' }),
    enabled: true,
    lastAttemptAt: '2026-08-02T10:20:00Z',
    lastSuccessAt: '2026-08-02T10:20:00Z',
    lastCheckedAt: '2026-08-02T10:30:00Z',
    lastOutcome: 'APPLIED',
    installedGameCount: 412,
    ...overrides,
  };
}
