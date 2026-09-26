import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { RouterModule } from '@angular/router';
import { FnaShellComponent } from './fna-shell.component';

describe('FnaShellComponent', () => {
  const createComponent = createComponentFactory({
    component: FnaShellComponent,
    imports: [RouterModule.forRoot([])],
  });

  let spectator: Spectator<FnaShellComponent>;

  beforeEach(() => {
    spectator = createComponent();
  });

  it('renders the section links in order', () => {
    const navLinks = spectator.queryAll('nav a');

    expect(navLinks.length).toBe(3);
    expect(navLinks[0].textContent).toContain('Articles');
    expect(navLinks[1].textContent).toContain('Portfolio');
    expect(navLinks[2].textContent).toContain('Briefing');
  });

  it('links relative to the shell route so it works under any mount point', () => {
    const hrefs = spectator.queryAll('nav a').map((link) => link.getAttribute('href'));

    expect(hrefs).toEqual(['/articles', '/portfolio', '/briefing']);
  });
});
