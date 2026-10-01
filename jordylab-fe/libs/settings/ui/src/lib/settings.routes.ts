import { Route } from '@angular/router';
import { AiModelsPageComponent } from './ai-models-page/ai-models-page.component';
import { UsersPageComponent } from './users-page/users-page.component';

export const settingsRoutes: Route[] = [
  { path: '', redirectTo: 'users', pathMatch: 'full' },
  { path: 'users', component: UsersPageComponent },
  { path: 'ai-models', component: AiModelsPageComponent },
];
