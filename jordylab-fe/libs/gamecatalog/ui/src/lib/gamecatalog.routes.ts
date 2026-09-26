import { Route } from '@angular/router';

export const gamecatalogRoutes: Route[] = [
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
    loadComponent: () =>
      import('./source-manager/source-manager.component').then(
        (m) => m.SourceManagerComponent,
      ),
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./game-detail/game-detail.component').then(
        (m) => m.GameDetailComponent,
      ),
  },
  { path: '', redirectTo: 'grid', pathMatch: 'full' },
];
