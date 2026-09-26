import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

@Component({
  selector: 'lib-gamecatalog-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, HlmButtonDirective],
  templateUrl: './gamecatalog-shell.component.html',
})
export class GamecatalogShellComponent {}
