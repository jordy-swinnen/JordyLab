import { Route } from '@angular/router';

export const appRoutes: Route[] = [
  {
    path: 'articles',
    loadComponent: () =>
      import('@jordylab-fe/fna/ui').then((m) => m.ArticleListComponent),
  },
  {
    path: 'portfolio',
    loadComponent: () =>
      import('@jordylab-fe/fna/ui').then((m) => m.PortfolioManagerComponent),
  },
  {
    path: 'briefing',
    loadComponent: () =>
      import('@jordylab-fe/fna/ui').then((m) => m.BriefingDisplayComponent),
  },
  { path: '', redirectTo: 'articles', pathMatch: 'full' },
];
