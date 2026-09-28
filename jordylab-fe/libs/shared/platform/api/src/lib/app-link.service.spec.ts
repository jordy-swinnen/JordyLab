import { Router } from '@angular/router';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { AuthService } from '@jordylab-fe/shared/auth';
import { AppLinkService } from './app-link.service';
import { PlatformService } from './platform.service';

const { addListener } = vi.hoisted(() => ({
  addListener: vi.fn(),
}));

vi.mock('@capacitor/app', () => ({
  App: { addListener },
}));

describe('AppLinkService', () => {
  let spectator: SpectatorService<AppLinkService>;
  const completeNativeLogin = vi.fn().mockResolvedValue(undefined);
  const navigateByUrl = vi.fn().mockResolvedValue(true);

  const createService = createServiceFactory({
    service: AppLinkService,
    providers: [
      { provide: AuthService, useValue: { completeNativeLogin } },
      { provide: Router, useValue: { navigateByUrl } },
    ],
  });

  function create(isNative: boolean) {
    spectator = createService({
      providers: [{ provide: PlatformService, useValue: { isNative: () => isNative } }],
    });
  }

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('does not register a listener on web', () => {
    create(false);

    spectator.service.listen();

    expect(addListener).not.toHaveBeenCalled();
  });

  it('completes the native login flow for a /mobile/callback URL', async () => {
    create(true);
    spectator.service.listen();
    const handler = addListener.mock.calls[0][1] as (event: { url: string }) => void;

    const callbackUrl = 'https://app.jordylab.test/mobile/callback?code=abc&state=xyz';
    handler({ url: callbackUrl });
    await Promise.resolve();

    expect(completeNativeLogin).toHaveBeenCalledWith(callbackUrl);
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('routes a settings-users screen tap to /settings/users', async () => {
    create(true);
    spectator.service.listen();
    const handler = addListener.mock.calls[0][1] as (event: { url: string }) => void;

    handler({ url: 'https://app.jordylab.test/mobile/open?screen=settings-users' });
    await Promise.resolve();

    expect(navigateByUrl).toHaveBeenCalledWith('/settings/users');
  });

  it('routes a fna-briefing screen tap to /fna/briefing', async () => {
    create(true);
    spectator.service.listen();
    const handler = addListener.mock.calls[0][1] as (event: { url: string }) => void;

    handler({ url: 'https://app.jordylab.test/mobile/open?screen=fna-briefing' });
    await Promise.resolve();

    expect(navigateByUrl).toHaveBeenCalledWith('/fna/briefing');
  });

  it('does nothing for an unknown screen value', async () => {
    create(true);
    spectator.service.listen();
    const handler = addListener.mock.calls[0][1] as (event: { url: string }) => void;

    handler({ url: 'https://app.jordylab.test/mobile/open?screen=unknown-screen' });
    await Promise.resolve();

    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
