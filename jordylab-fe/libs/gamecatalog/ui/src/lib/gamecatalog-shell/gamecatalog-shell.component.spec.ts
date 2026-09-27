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

  it('only hosts the router outlet — section navigation belongs to the app shell', () => {
    expect(spectator.query('router-outlet')).toBeTruthy();
    expect(spectator.query('nav')).toBeNull();
  });
});
