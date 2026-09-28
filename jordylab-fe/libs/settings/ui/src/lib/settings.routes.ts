import { Route } from '@angular/router';
import { UsersPageComponent } from './users-page/users-page.component';

export const settingsRoutes: Route[] = [
  { path: '', redirectTo: 'users', pathMatch: 'full' },
  { path: 'users', component: UsersPageComponent },
];
