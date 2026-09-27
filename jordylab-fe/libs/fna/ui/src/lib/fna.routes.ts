import { Route } from '@angular/router';
import { roleGuard } from '@jordylab-fe/shared/auth';
import { FnaShellComponent } from './fna-shell/fna-shell.component';

/**
 * Guarded here (not only where the host mounts these routes) so the standalone `apps/fna` dev
 * harness — which wraps this same array behind only `authGuard` — enforces the same admin-only
 * access matrix as the host.
 */
export const fnaRoutes: Route[] = [
  {
    path: '',
    component: FnaShellComponent,
    canActivate: [roleGuard('admin')],
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
