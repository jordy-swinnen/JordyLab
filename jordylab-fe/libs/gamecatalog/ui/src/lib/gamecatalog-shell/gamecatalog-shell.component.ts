import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Route container for the domain. Section navigation lives in the host sidebar (and the dev harness header). */
@Component({
  selector: 'lib-gamecatalog-shell',
  standalone: true,
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './gamecatalog-shell.component.html',
})
export class GamecatalogShellComponent {}
