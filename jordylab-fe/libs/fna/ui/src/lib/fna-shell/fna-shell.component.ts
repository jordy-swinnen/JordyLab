import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

@Component({
  selector: 'lib-fna-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, HlmButtonDirective],
  templateUrl: './fna-shell.component.html',
})
export class FnaShellComponent {}
