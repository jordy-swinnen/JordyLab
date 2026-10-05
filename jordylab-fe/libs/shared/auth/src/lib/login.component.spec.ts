import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { AuthService } from './auth.service';
import { BiometricUnlockService } from './biometric-unlock.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let spectator: Spectator<LoginComponent>;
  let login: ReturnType<typeof vi.fn>;
  let unlock: ReturnType<typeof vi.fn>;
  const isAuthenticated = signal(false);
  const biometricEnabled = signal(false);
  const biometricFailure = signal<string | null>(null);
  const nativeFailure = signal<string | null>(null);

  const createComponent = createComponentFactory({
    component: LoginComponent,
    providers: [
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
      {
        provide: AuthService,
        useValue: {
          login: (...args: unknown[]) => login(...args),
          isAuthenticated: isAuthenticated.asReadonly(),
          nativeFailure: nativeFailure.asReadonly(),
        },
      },
      {
        provide: BiometricUnlockService,
        useValue: {
          enabled: biometricEnabled.asReadonly(),
          failure: biometricFailure.asReadonly(),
          refresh: () => Promise.resolve(),
          unlock: (...args: unknown[]) => unlock(...args),
        },
      },
    ],
  });

  beforeEach(() => {
    login = vi.fn().mockResolvedValue(undefined);
    unlock = vi.fn().mockResolvedValue(true);
    isAuthenticated.set(false);
    biometricEnabled.set(false);
    biometricFailure.set(null);
    nativeFailure.set(null);
    spectator = createComponent();
  });

  it('renders a sign-in button', () => {
    expect(spectator.query('button')).toHaveText('Sign in with Keycloak');
  });

  it('calls AuthService.login() when the button is clicked', () => {
    spectator.click('button');

    expect(login).toHaveBeenCalled();
  });

  it('offers fingerprint unlock only when the user opted in', () => {
    expect(spectator.queryAll('button')).toHaveLength(1);

    biometricEnabled.set(true);
    spectator.detectChanges();

    expect(spectator.queryAll('button')).toHaveLength(2);
    spectator.click(spectator.queryAll('button')[1]);
    expect(unlock).toHaveBeenCalled();
  });

  it('leaves the login page as soon as the session becomes authenticated (native callback)', () => {
    const navigateByUrl = vi
      .spyOn(spectator.inject(Router), 'navigateByUrl')
      .mockResolvedValue(true);

    isAuthenticated.set(true);
    spectator.detectChanges();

    expect(navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('tells the user why a fingerprint unlock or a session refresh failed', () => {
    biometricFailure.set('The fingerprint check was cancelled.');
    spectator.detectChanges();

    expect(spectator.query('[role="alert"]')?.textContent).toContain(
      'cancelled',
    );

    biometricFailure.set(null);
    nativeFailure.set('Your session could not be refreshed. Sign in again.');
    spectator.detectChanges();

    expect(spectator.query('[role="alert"]')?.textContent).toContain(
      'could not be refreshed',
    );
  });
});

describe('LoginComponent return address', () => {
  const isAuthenticated = signal(false);
  let returnUrl: string | null;

  const createComponent = createComponentFactory({
    component: LoginComponent,
    providers: [
      {
        provide: ActivatedRoute,
        useFactory: () => ({
          snapshot: { queryParamMap: convertToParamMap(returnUrl === null ? {} : { returnUrl }) },
        }),
      },
      {
        provide: AuthService,
        useValue: { login: vi.fn(), isAuthenticated: isAuthenticated.asReadonly(), nativeFailure: signal(null).asReadonly() },
      },
      {
        provide: BiometricUnlockService,
        useValue: { enabled: signal(false).asReadonly(), failure: signal(null).asReadonly(), refresh: () => Promise.resolve() },
      },
    ],
  });

  const signInReturningTo = (candidate: string | null) => {
    returnUrl = candidate;
    isAuthenticated.set(false);
    const spectator = createComponent();
    const navigateByUrl = vi.spyOn(spectator.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    isAuthenticated.set(true);
    spectator.detectChanges();

    return navigateByUrl;
  };

  it('goes back to the share screen a signed-out share was heading for', () => {
    expect(signInReturningTo('/mobile/share')).toHaveBeenCalledWith('/mobile/share');
  });

  it('goes to the start page when nothing was remembered', () => {
    expect(signInReturningTo(null)).toHaveBeenCalledWith('/');
  });

  it('ignores a return address that leaves the app or loops back to the login page', () => {
    expect(signInReturningTo('//evil.example/x')).toHaveBeenCalledWith('/');
    expect(signInReturningTo('https://evil.example/')).toHaveBeenCalledWith('/');
    expect(signInReturningTo('/login?returnUrl=/fna')).toHaveBeenCalledWith('/');
  });
});
