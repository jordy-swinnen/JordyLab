import { Route } from '@angular/router';
import { roleGuard } from '@jordylab-fe/shared/auth';
import { GamecatalogShellComponent } from './gamecatalog-shell/gamecatalog-shell.component';

/**
 * Guarded here (not only where the host mounts these routes) so the standalone `apps/gamecatalog`
 * dev harness — which wraps this same array behind only `authGuard` — enforces the same access
 * matrix as the host: reads and chat are open to admin and guest; writes (sources) are admin-only.
 */
export const gamecatalogRoutes: Route[] = [
  {
    path: '',
    component: GamecatalogShellComponent,
    canActivate: [roleGuard('admin', 'guest')],
    children: [
      {
        path: 'grid',
        loadComponent: () =>
          import('./game-grid/game-grid.component').then((m) => m.GameGridComponent),
      },
      {
        path: 'chat',
        loadComponent: () =>
          import('./game-chat/game-chat.component').then((m) => m.GameChatComponent),
      },
      {
        path: 'sources',
        canActivate: [roleGuard('admin')],
        loadComponent: () =>
          import('./source-manager/source-manager.component').then(
            (m) => m.SourceManagerComponent,
          ),
      },
      {
        path: 'switch',
        canActivate: [roleGuard('admin')],
        loadComponent: () =>
          import('./switch-game/switch-game.component').then((m) => m.SwitchGameComponent),
      },
      {
        path: ':id',
        loadComponent: () =>
          import('./game-detail/game-detail.component').then(
            (m) => m.GameDetailComponent,
          ),
      },
      { path: '', redirectTo: 'grid', pathMatch: 'full' },
    ],
  },
];
