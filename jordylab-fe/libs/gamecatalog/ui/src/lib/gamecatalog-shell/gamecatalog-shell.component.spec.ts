import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { RouterModule } from '@angular/router';
import { GamecatalogShellComponent } from './gamecatalog-shell.component';

describe('GamecatalogShellComponent', () => {
  const createComponent = createComponentFactory({
    component: GamecatalogShellComponent,
    imports: [RouterModule.forRoot([])],
  });

  let spectator: Spectator<GamecatalogShellComponent>;

  beforeEach(() => {
    spectator = createComponent();
  });

  it('renders the section links in order', () => {
    const navLinks = spectator.queryAll('nav a');

    expect(navLinks.length).toBe(3);
    expect(navLinks[0].textContent).toContain('Library');
    expect(navLinks[1].textContent).toContain('Chat');
    expect(navLinks[2].textContent).toContain('Sources');
  });

  it('links relative to the shell route so it works under any mount point', () => {
    const hrefs = spectator.queryAll('nav a').map((link) => link.getAttribute('href'));

    expect(hrefs).toEqual(['/grid', '/chat', '/sources']);
  });
});
