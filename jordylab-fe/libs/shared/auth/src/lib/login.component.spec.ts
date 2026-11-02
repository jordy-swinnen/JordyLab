import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { AuthService } from './auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let spectator: Spectator<LoginComponent>;
  let login: ReturnType<typeof vi.fn>;
  const isAuthenticated = signal(false);

  const createComponent = createComponentFactory({
    component: LoginComponent,
    providers: [
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
      {
        provide: AuthService,
        useValue: {
          login: (...args: unknown[]) => login(...args),
          isAuthenticated: isAuthenticated.asReadonly(),
        },
      },
    ],
  });

  beforeEach(() => {
    login = vi.fn().mockResolvedValue(undefined);
    isAuthenticated.set(false);
    spectator = createComponent();
  });

  it('renders a sign-in button', () => {
    expect(spectator.query('button')).toHaveText('Sign in with Keycloak');
  });

  it('calls AuthService.login() when the button is clicked', () => {
    spectator.click('button');

    expect(login).toHaveBeenCalledWith('/');
  });

  it('leaves the login page as soon as the session becomes authenticated (silent SSO)', () => {
    const navigateByUrl = vi
      .spyOn(spectator.inject(Router), 'navigateByUrl')
      .mockResolvedValue(true);

    isAuthenticated.set(true);
    spectator.detectChanges();

    expect(navigateByUrl).toHaveBeenCalledWith('/');
  });
});

describe('LoginComponent return address', () => {
  const isAuthenticated = signal(false);
  let returnUrl: string | null;
  const login = vi.fn(() => Promise.resolve());

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
        useValue: {
          login: (...args: unknown[]) => login(...args),
          isAuthenticated: isAuthenticated.asReadonly(),
        },
      },
    ],
  });

  beforeEach(() => login.mockClear());

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
    expect(signInReturningTo('/share?text=remember%20me')).toHaveBeenCalledWith('/share?text=remember%20me');
  });

  it('goes to the start page when nothing was remembered', () => {
    expect(signInReturningTo(null)).toHaveBeenCalledWith('/');
  });

  it('ignores a return address that leaves the app or loops back to the login page', () => {
    expect(signInReturningTo('//evil.example/x')).toHaveBeenCalledWith('/');
    expect(signInReturningTo('https://evil.example/')).toHaveBeenCalledWith('/');
    expect(signInReturningTo('/login?returnUrl=/fna')).toHaveBeenCalledWith('/');
  });

  it('passes the remembered address to Keycloak so the redirect lands back on it', () => {
    returnUrl = '/share?text=remember%20me';
    isAuthenticated.set(false);
    const spectator = createComponent();

    void spectator.component.onLogin();

    expect(login).toHaveBeenCalledWith('/share?text=remember%20me');
  });
});
