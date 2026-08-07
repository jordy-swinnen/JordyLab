import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { AuthService } from './auth/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, HlmButtonDirective],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  #auth = inject(AuthService);

  username = this.#auth.username;

  async onLogout(): Promise<void> {
    await this.#auth.logout();
  }
}
