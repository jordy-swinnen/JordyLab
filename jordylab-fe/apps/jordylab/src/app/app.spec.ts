import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { RouterModule } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { App } from './app';
import { AuthService } from './auth/auth.service';

describe('App', () => {
  const createComponent = createComponentFactory({
    component: App,
    imports: [RouterModule.forRoot([])],
    providers: [provideHttpClient(), { provide: AuthService, useValue: { username: () => null, logout: () => Promise.resolve() } }],
  });

  let spectator: Spectator<App>;

  beforeEach(() => {
    spectator = createComponent();
  });

  it('should render navigation links', () => {
    const navLinks = spectator.queryAll('nav a');
    expect(navLinks.length).toBe(2);
    expect(navLinks[0].textContent).toContain('FNA');
    expect(navLinks[1].textContent).toContain('Game Catalog');
  });
});
