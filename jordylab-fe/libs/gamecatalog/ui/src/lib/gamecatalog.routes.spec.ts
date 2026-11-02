import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { AuthService } from '@jordylab-fe/shared/auth';
import { gamecatalogRoutes } from './gamecatalog.routes';

describe('gamecatalogRoutes', () => {
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

  const shell = () => gamecatalogRoutes[0];
  const child = (path: string) =>
    shell().children?.find((route) => route.path === path);

  it('lists grid, libbot, chat redirect, sources, consoles, bulk consoles, the old switch redirects and detail under the shell route', () => {
    expect(shell().children?.map((route) => route.path)).toEqual([
      'grid',
      'libbot',
      'chat',
      'sources',
      'consoles',
      'consoles/:id/games/bulk',
      'switch',
      'switch/bulk',
      ':id',
      '',
    ]);
  });

  it('guards the shell for admin or a person with the app grant', async () => {
    roles.set(['guest', 'app-gamecatalog']);

    await expect(runGuard(shell().canActivate)).resolves.toBe(true);
  });

  it('sends an approved guest without the app grant away from the shell', async () => {
    roles.set(['guest']);

    await expect(runGuard(shell().canActivate)).resolves.toBe('parsed:/no-apps');
  });

  it('sends a role-less account away from the shell', async () => {
    await expect(runGuard(shell().canActivate)).resolves.toBe(
      'parsed:/awaiting-approval',
    );
  });

  it('guards the sources child to admin only', async () => {
    // A normal approved guest holds the Game Catalog grant, so their landing route exists.
    roles.set(['guest', 'app-gamecatalog']);

    await expect(runGuard(child('sources')?.canActivate)).resolves.toBe(
      'parsed:/games/grid',
    );
  });

  it('lets an admin reach sources', async () => {
    roles.set(['admin']);

    await expect(runGuard(child('sources')?.canActivate)).resolves.toBe(true);
  });

  it('guards the consoles children to admin only', async () => {
    roles.set(['guest', 'app-gamecatalog']);

    for (const path of ['consoles', 'consoles/:id/games/bulk']) {
      await expect(runGuard(child(path)?.canActivate)).resolves.toBe('parsed:/games/grid');
    }
  });

  it('lets an admin reach consoles', async () => {
    roles.set(['admin']);

    await expect(runGuard(child('consoles')?.canActivate)).resolves.toBe(true);
  });

  it('sends the old switch addresses to consoles', () => {
    expect(child('switch')?.redirectTo).toBe('consoles');
    expect(child('switch/bulk')?.redirectTo).toBe('consoles');
  });

  it('leaves grid, libbot and detail open to whatever the shell already let through', () => {
    for (const path of ['grid', 'libbot', ':id']) {
      expect(child(path)?.canActivate).toBeUndefined();
    }
  });
});
