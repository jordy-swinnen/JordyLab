import { TestBed } from '@angular/core/testing';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { PlatformService } from '@jordylab-fe/shared/platform/api';
import { appRoutes } from './app.routes';

describe('Settings → App route', () => {
  const route = appRoutes.find(
    (candidate) => candidate.path === 'settings/app',
  );
  const nativeOnlyGuard = route?.canActivate?.[0] as CanActivateFn;

  function runGuard(isNative: boolean): unknown {
    TestBed.configureTestingModule({
      providers: [
        { provide: PlatformService, useValue: { isNative: () => isNative } },
      ],
    });

    return TestBed.runInInjectionContext(() =>
      nativeOnlyGuard({} as never, {} as never),
    );
  }

  it('is declared before the admin-only settings area so guests reach it', () => {
    const settingsIndex = appRoutes.findIndex(
      (candidate) => candidate.path === 'settings',
    );

    expect(
      appRoutes.findIndex((candidate) => candidate.path === 'settings/app'),
    ).toBeLessThan(settingsIndex);
  });

  it('opens inside the native app', () => {
    expect(runGuard(true)).toBe(true);
  });

  it('sends a web visitor home', () => {
    const result = runGuard(false) as UrlTree;

    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/');
  });
});
