import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { RouterModule } from '@angular/router';
import { App } from './app';

describe('App', () => {
  const createComponent = createComponentFactory({
    component: App,
    imports: [RouterModule.forRoot([])],
  });

  let spectator: Spectator<App>;

  beforeEach(() => {
    spectator = createComponent();
  });

  it('renders the brand link and a router outlet for the domain routes', () => {
    expect(spectator.query('header a')).toBeTruthy();
    expect(spectator.query('router-outlet')).toBeTruthy();
  });
});
