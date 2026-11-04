import { Route } from '@angular/router';
import { roleGuard } from '@jordylab-fe/shared/auth';
import { AiModelsPageComponent } from './ai-models-page/ai-models-page.component';
import { MachinesPageComponent } from './machines-page/machines-page.component';
import { UsersPageComponent } from './users-page/users-page.component';

/**
 * The admin-only Settings tabs, mounted by the host inside the composed `/settings` route so the
 * guest Feedback tab can live beside them without this lib importing the feedback domain (spec 017
 * FR-001/FR-003). Each tab re-guards itself: the host's `/settings` guard admits admin and guest,
 * the tabs decide who may enter.
 */
export const settingsAdminRoutes: Route[] = [
  { path: 'users', canActivate: [roleGuard('admin')], component: UsersPageComponent },
  { path: 'ai-models', canActivate: [roleGuard('admin')], component: AiModelsPageComponent },
  { path: 'machines', canActivate: [roleGuard('admin')], component: MachinesPageComponent },
];

/**
 * Standalone Settings routes (admin only) — kept for the dev harness and any consumer that mounts
 * the whole area itself; the host composes `settingsAdminRoutes` with the feedback tabs instead.
 */
export const settingsRoutes: Route[] = [
  { path: '', redirectTo: 'users', pathMatch: 'full' },
  ...settingsAdminRoutes,
];
