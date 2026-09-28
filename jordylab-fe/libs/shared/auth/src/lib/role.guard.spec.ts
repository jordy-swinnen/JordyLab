import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { AuthService } from './auth.service';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  // Real signal instances back the mock's reactive state, so tests drive it with .set(...)
  // directly instead of injecting the mock back out and casting it.
  const roles = signal<string[]>([]);
  let authenticated: boolean;
  let init: ReturnType<typeof vi.fn>;
  let parseUrl: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    roles.set([]);
    authenticated = false;
    init = vi.fn();
    parseUrl = vi.fn().mockImplementation((url: string) => `parsed:${url}`);

    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: () => authenticated,
            init,
            roles: roles.asReadonly(),
          },
        },
        { provide: Router, useValue: { parseUrl } },
      ],
    });
  });

  const runGuard = (...allowedRoles: Array<'admin' | 'guest'>) =>
    TestBed.runInInjectionContext(() =>
      roleGuard(...allowedRoles)(
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
      ),
    );

  it('allows an admin onto an admin route without re-initializing', async () => {
    authenticated = true;
    roles.set(['admin']);

    const result = await runGuard('admin');

    expect(result).toBe(true);
    expect(init).not.toHaveBeenCalled();
  });

  it('allows a guest onto a route open to admin and guest', async () => {
    authenticated = true;
    roles.set(['guest']);

    const result = await runGuard('admin', 'guest');

    expect(result).toBe(true);
  });

  it('redirects to /login when the visitor is not authenticated', async () => {
    init.mockResolvedValueOnce(false);

    const result = await runGuard('admin');

    expect(parseUrl).toHaveBeenCalledWith('/login');
    expect(result).toBe('parsed:/login');
  });

  it('authenticates before deciding and allows a matching role', async () => {
    init.mockResolvedValueOnce(true);
    roles.set(['guest']);

    const result = await runGuard('guest');

    expect(result).toBe(true);
  });

  it('sends an authenticated account without an app role to /awaiting-approval', async () => {
    authenticated = true;
    roles.set(['offline_access']);

    const result = await runGuard('admin');

    expect(parseUrl).toHaveBeenCalledWith('/awaiting-approval');
    expect(result).toBe('parsed:/awaiting-approval');
  });

  it('sends a guest denied an admin route to their own landing route', async () => {
    authenticated = true;
    roles.set(['guest']);

    const result = await runGuard('admin');

    expect(parseUrl).toHaveBeenCalledWith('/games/grid');
    expect(result).toBe('parsed:/games/grid');
  });

  it('sends an admin denied a guest-only route to their own landing route', async () => {
    authenticated = true;
    roles.set(['admin']);

    const result = await runGuard('guest');

    expect(parseUrl).toHaveBeenCalledWith('/fna');
    expect(result).toBe('parsed:/fna');
  });
});
