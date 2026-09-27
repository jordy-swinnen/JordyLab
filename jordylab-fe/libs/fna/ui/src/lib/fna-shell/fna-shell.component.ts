import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Route container for the domain. Section navigation lives in the host sidebar (and the dev harness header). */
@Component({
  selector: 'lib-fna-shell',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './fna-shell.component.html',
})
export class FnaShellComponent {}
