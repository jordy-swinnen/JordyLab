import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from '@jordylab-fe/shared/auth';
import { AppLinkService } from './app-link.service';
import { PlatformService } from './platform.service';

const { addListener } = vi.hoisted(() => ({
  addListener: vi.fn(),
}));

vi.mock('@capacitor/app', () => ({
  App: { addListener },
}));

function createService(isNative: boolean) {
  const completeNativeLogin = vi.fn().mockResolvedValue(undefined);
  const navigateByUrl = vi.fn().mockResolvedValue(true);

  TestBed.configureTestingModule({
    providers: [
      { provide: PlatformService, useValue: { isNative: () => isNative } },
      { provide: AuthService, useValue: { completeNativeLogin } },
      { provide: Router, useValue: { navigateByUrl } },
    ],
  });

  return { service: TestBed.inject(AppLinkService), completeNativeLogin, navigateByUrl };
}

describe('AppLinkService', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('does not register a listener on web', () => {
    const { service } = createService(false);

    service.listen();

    expect(addListener).not.toHaveBeenCalled();
  });

  it('completes the native login flow for a /mobile/callback URL', async () => {
    const { service, completeNativeLogin, navigateByUrl } = createService(true);
    service.listen();
    const handler = addListener.mock.calls[0][1] as (event: { url: string }) => void;

    const callbackUrl = 'https://app.jordylab.test/mobile/callback?code=abc&state=xyz';
    handler({ url: callbackUrl });
    await Promise.resolve();

    expect(completeNativeLogin).toHaveBeenCalledWith(callbackUrl);
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('routes a settings-users screen tap to /settings/users', async () => {
    const { service, navigateByUrl } = createService(true);
    service.listen();
    const handler = addListener.mock.calls[0][1] as (event: { url: string }) => void;

    handler({ url: 'https://app.jordylab.test/mobile/open?screen=settings-users' });
    await Promise.resolve();

    expect(navigateByUrl).toHaveBeenCalledWith('/settings/users');
  });

  it('routes a fna-briefing screen tap to /fna/briefing', async () => {
    const { service, navigateByUrl } = createService(true);
    service.listen();
    const handler = addListener.mock.calls[0][1] as (event: { url: string }) => void;

    handler({ url: 'https://app.jordylab.test/mobile/open?screen=fna-briefing' });
    await Promise.resolve();

    expect(navigateByUrl).toHaveBeenCalledWith('/fna/briefing');
  });

  it('does nothing for an unknown screen value', async () => {
    const { service, navigateByUrl } = createService(true);
    service.listen();
    const handler = addListener.mock.calls[0][1] as (event: { url: string }) => void;

    handler({ url: 'https://app.jordylab.test/mobile/open?screen=unknown-screen' });
    await Promise.resolve();

    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
