import { Route } from '@angular/router';

export const appRoutes: Route[] = [
  {
    path: 'grid',
    loadComponent: () =>
      import('@jordylab-fe/gamecatalog/ui').then((m) => m.GameGridComponent),
  },
  {
    path: 'chat',
    loadComponent: () =>
      import('@jordylab-fe/gamecatalog/ui').then((m) => m.GameChatComponent),
  },
  {
    path: 'sources',
    loadComponent: () =>
      import('@jordylab-fe/gamecatalog/ui').then((m) => m.SourceManagerComponent),
  },
  {
    path: ':id',
    loadComponent: () =>
      import('@jordylab-fe/gamecatalog/ui').then((m) => m.GameDetailComponent),
  },
  { path: '', redirectTo: 'grid', pathMatch: 'full' },
];
