import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let isAuthenticated: boolean;
  let init: ReturnType<typeof vi.fn>;
  let parseUrl: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    isAuthenticated = false;
    init = vi.fn();
    parseUrl = vi.fn().mockReturnValue('parsed-login-url');

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAuthenticated: () => isAuthenticated, init } },
        { provide: Router, useValue: { parseUrl } },
      ],
    });
  });

  const runGuard = () =>
    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );

  it('allows navigation when already authenticated', async () => {
    isAuthenticated = true;

    const result = await runGuard();

    expect(result).toBe(true);
    expect(init).not.toHaveBeenCalled();
  });

  it('allows navigation when init() authenticates', async () => {
    init.mockResolvedValueOnce(true);

    const result = await runGuard();

    expect(result).toBe(true);
  });

  it('redirects to /login when init() does not authenticate', async () => {
    init.mockResolvedValueOnce(false);

    const result = await runGuard();

    expect(parseUrl).toHaveBeenCalledWith('/login');
    expect(result).toBe('parsed-login-url');
  });
});
