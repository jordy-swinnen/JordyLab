import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { AuthService, CurrentUser } from './auth.service';
import { LoginComponent } from './login.component';

const USER: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  username: 'jordy',
  displayName: null,
  role: 'admin',
  status: 'APPROVED',
  appGrants: [],
};

describe('LoginComponent', () => {
  const isAuthenticated = signal(false);
  let login: ReturnType<typeof vi.fn>;
  let navigateByUrl: ReturnType<typeof vi.fn>;
  let returnUrl: string | null;

  let spectator: Spectator<LoginComponent>;
  const createComponent = createComponentFactory({
    component: LoginComponent,
    providers: [
      {
        provide: ActivatedRoute,
        useValue: {
          get snapshot() {
            return { queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}) };
          },
        },
      },
      {
        provide: Router,
        useValue: { navigateByUrl: (...args: unknown[]) => navigateByUrl(...args) },
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

  beforeEach(() => {
    login = vi.fn().mockResolvedValue(USER);
    navigateByUrl = vi.fn().mockResolvedValue(true);
    isAuthenticated.set(false);
    returnUrl = null;
    spectator = createComponent();
  });

  it('renders a username and password form', () => {
    expect(spectator.query('[data-testid="login-username"]')).toExist();
    expect(spectator.query('[data-testid="login-password"]')).toExist();
    expect(spectator.query('[data-testid="login-submit"]')).toHaveText('Sign in');
  });

  it('signs in with the typed credentials and leaves the page', async () => {
    spectator.typeInElement('jordy', '[data-testid="login-username"]');
    spectator.typeInElement('correct horse battery', '[data-testid="login-password"]');
    spectator.click('[data-testid="login-submit"]');
    await spectator.fixture.whenStable();

    expect(login).toHaveBeenCalledWith('jordy', 'correct horse battery');
    expect(navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('returns to the sanitised returnUrl after signing in', async () => {
    returnUrl = '/games/grid';
    spectator = createComponent();
    spectator.typeInElement('jordy', '[data-testid="login-username"]');
    spectator.typeInElement('pw', '[data-testid="login-password"]');
    spectator.click('[data-testid="login-submit"]');
    await spectator.fixture.whenStable();

    expect(navigateByUrl).toHaveBeenCalledWith('/games/grid');
  });

  it('never returns to an external address', async () => {
    returnUrl = 'https://evil.example';
    spectator = createComponent();
    spectator.typeInElement('jordy', '[data-testid="login-username"]');
    spectator.typeInElement('pw', '[data-testid="login-password"]');
    spectator.click('[data-testid="login-submit"]');
    await spectator.fixture.whenStable();

    expect(navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('shows a clear message when the credentials are refused', async () => {
    login.mockRejectedValue(
      new HttpErrorResponse({ status: 401, error: { reason: 'INVALID_CREDENTIALS' } }),
    );
    spectator.typeInElement('jordy', '[data-testid="login-username"]');
    spectator.typeInElement('wrong', '[data-testid="login-password"]');
    spectator.click('[data-testid="login-submit"]');
    await spectator.fixture.whenStable();
    spectator.detectChanges();

    expect(spectator.query('[data-testid="login-error"]')).toHaveText('Invalid username or password.');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('leaves as soon as a session already exists', () => {
    isAuthenticated.set(true);
    spectator.detectChanges();

    expect(navigateByUrl).toHaveBeenCalledWith('/');
  });
});
