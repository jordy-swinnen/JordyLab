import { Route } from '@angular/router';
import { FnaShellComponent } from './fna-shell/fna-shell.component';

export const fnaRoutes: Route[] = [
  {
    path: '',
    component: FnaShellComponent,
    children: [
      {
        path: 'articles',
        loadComponent: () =>
          import('./article-list/article-list.component').then(
            (m) => m.ArticleListComponent,
          ),
      },
      {
        path: 'portfolio',
        loadComponent: () =>
          import('./portfolio-manager/portfolio-manager.component').then(
            (m) => m.PortfolioManagerComponent,
          ),
      },
      {
        path: 'briefing',
        loadComponent: () =>
          import('./briefing-display/briefing-display.component').then(
            (m) => m.BriefingDisplayComponent,
          ),
      },
      { path: '', redirectTo: 'articles', pathMatch: 'full' },
    ],
  },
];
