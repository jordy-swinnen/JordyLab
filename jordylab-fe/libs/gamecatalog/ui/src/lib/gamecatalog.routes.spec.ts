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

  it('lists grid, chat, sources, switch and detail under the shell route', () => {
    expect(shell().children?.map((route) => route.path)).toEqual([
      'grid',
      'chat',
      'sources',
      'switch',
      ':id',
      '',
    ]);
  });

  it('guards the shell for admin or guest', async () => {
    roles.set(['guest']);

    await expect(runGuard(shell().canActivate)).resolves.toBe(true);
  });

  it('sends a role-less account away from the shell', async () => {
    await expect(runGuard(shell().canActivate)).resolves.toBe(
      'parsed:/awaiting-approval',
    );
  });

  it('guards the sources child to admin only', async () => {
    roles.set(['guest']);

    await expect(runGuard(child('sources')?.canActivate)).resolves.toBe(
      'parsed:/games/grid',
    );
  });

  it('lets an admin reach sources', async () => {
    roles.set(['admin']);

    await expect(runGuard(child('sources')?.canActivate)).resolves.toBe(true);
  });

  it('guards the switch child to admin only', async () => {
    roles.set(['guest']);

    await expect(runGuard(child('switch')?.canActivate)).resolves.toBe(
      'parsed:/games/grid',
    );
  });

  it('lets an admin reach switch', async () => {
    roles.set(['admin']);

    await expect(runGuard(child('switch')?.canActivate)).resolves.toBe(true);
  });

  it('leaves grid, chat and detail open to whatever the shell already let through', () => {
    for (const path of ['grid', 'chat', ':id']) {
      expect(child(path)?.canActivate).toBeUndefined();
    }
  });
});
