import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import * as QRCode from 'qrcode';
import { AndroidAppQrEntryComponent } from './android-app-qr-entry.component';

vi.mock('qrcode', () => ({
  toCanvas: vi.fn().mockResolvedValue(undefined),
}));

describe('AndroidAppQrEntryComponent', () => {
  const createComponent = createComponentFactory(AndroidAppQrEntryComponent);
  let spectator: Spectator<AndroidAppQrEntryComponent>;

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('renders a QR code pointing at the current origin', () => {
    spectator = createComponent();

    expect(QRCode.toCanvas).toHaveBeenCalledWith(
      spectator.query('canvas'),
      window.location.origin,
      { width: 160 },
    );
  });

  it('shows the install hint text', () => {
    spectator = createComponent();

    expect(spectator.element).toHaveText("Scan with your Android phone's camera");
  });
});
