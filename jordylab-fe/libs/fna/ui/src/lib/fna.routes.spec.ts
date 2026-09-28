import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { AuthService } from '@jordylab-fe/shared/auth';
import { fnaRoutes } from './fna.routes';

describe('fnaRoutes', () => {
  const roles = signal<string[]>([]);
  let parseUrl: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    roles.set([]);
    parseUrl = vi.fn().mockImplementation((url: string) => `parsed:${url}`);

    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: () => true,
            init: vi.fn(),
            roles: roles.asReadonly(),
          },
        },
        { provide: Router, useValue: { parseUrl } },
      ],
    });
  });

  const runGuard = (canActivate: CanActivateFn[] | undefined) =>
    TestBed.runInInjectionContext(() =>
      canActivate?.[0](
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
      ),
    );

  const shell = () => fnaRoutes[0];

  it('lists articles, portfolio and briefing under the shell route', () => {
    expect(shell().children?.map((route) => route.path)).toEqual([
      'articles',
      'portfolio',
      'briefing',
      '',
    ]);
  });

  it('guards the shell for admin only', async () => {
    roles.set(['admin']);

    await expect(runGuard(shell().canActivate)).resolves.toBe(true);
  });

  it('sends a guest away from the shell', async () => {
    roles.set(['guest']);

    await expect(runGuard(shell().canActivate)).resolves.toBe(
      'parsed:/games/grid',
    );
  });
});
