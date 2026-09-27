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

  it('only hosts the router outlet — section navigation belongs to the app shell', () => {
    expect(spectator.query('router-outlet')).toBeTruthy();
    expect(spectator.query('nav')).toBeNull();
  });
});
