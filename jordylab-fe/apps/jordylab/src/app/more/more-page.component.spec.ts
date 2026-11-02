import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { AuthService } from '@jordylab-fe/shared/auth';
import { MorePageComponent } from './more-page.component';

describe('MorePageComponent', () => {
  const isAdmin = signal(true);
  const createComponent = createComponentFactory({
    component: MorePageComponent,
    providers: [provideRouter([]), { provide: AuthService, useValue: { isAdmin } }],
  });
  let spectator: Spectator<MorePageComponent>;

  const labels = () => spectator.queryAll('a').map((link) => link.textContent?.trim());

  it('lists the administrator items and App settings for an administrator', () => {
    isAdmin.set(true);
    spectator = createComponent();

    expect(labels()).toEqual(['Sources', 'Consoles', 'Users', 'AI Models', 'App']);
  });

  it('lists only App settings for a guest', () => {
    isAdmin.set(false);
    spectator = createComponent();

    expect(labels()).toEqual(['App']);
  });
});
