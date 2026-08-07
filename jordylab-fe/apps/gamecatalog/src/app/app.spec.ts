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

  it('should render navigation links', () => {
    const navLinks = spectator.queryAll('nav a');
    expect(navLinks.length).toBe(3);
    expect(navLinks[0].textContent).toContain('Library');
    expect(navLinks[1].textContent).toContain('Chat');
    expect(navLinks[2].textContent).toContain('Sources');
  });
});
