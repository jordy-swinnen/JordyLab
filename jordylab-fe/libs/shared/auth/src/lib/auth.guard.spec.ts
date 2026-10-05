import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let isAuthenticated: boolean;
  let init: ReturnType<typeof vi.fn>;
  let createUrlTree: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    isAuthenticated = false;
    init = vi.fn();
    createUrlTree = vi.fn().mockReturnValue('login-url-tree');

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAuthenticated: () => isAuthenticated, init } },
        { provide: Router, useValue: { createUrlTree } },
      ],
    });
  });

  const runGuard = (url = '/fna') =>
    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot)
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

    expect(createUrlTree).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/fna' } });
    expect(result).toBe('login-url-tree');
  });

  it('remembers a share screen that was opened while signed out, so login can return to it', async () => {
    init.mockResolvedValueOnce(false);

    await runGuard('/mobile/share');

    expect(createUrlTree).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/mobile/share' } });
  });

  it('does not remember the root or the login page itself', async () => {
    init.mockResolvedValue(false);

    await runGuard('/');
    await runGuard('/login?returnUrl=%2Ffna');

    expect(createUrlTree).toHaveBeenNthCalledWith(1, ['/login'], {});
    expect(createUrlTree).toHaveBeenNthCalledWith(2, ['/login'], {});
  });
});
