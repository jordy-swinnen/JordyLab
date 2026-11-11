import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

interface CreatedMachine {
  readonly id: string;
  readonly name: string;
  readonly token: string;
}

/**
 * The desktop app's connect page (spec 024 US1, ticket D4). The app opens this page in the browser with a `state`, its
 * loopback `port` and its hostname; after the admin signs in, the page creates the machine token through the existing
 * machines endpoint and hands it back to the app on 127.0.0.1. Admin-only through the route guard: a guest never reaches it.
 */
@Component({
  selector: 'app-connect-desktop',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <main class="mx-auto flex max-w-xl flex-col gap-4 p-8">
      <h1 class="text-2xl font-semibold">Connect this PC</h1>
      @switch (state()) {
        @case ('working') {
          <p class="text-muted-foreground">Creating this machine's token…</p>
        }
        @case ('redirect') {
          <p class="text-muted-foreground">
            Connected. Returning to the app… If nothing happens, use the link below.
          </p>
          <a class="underline" [href]="callbackUrl()">Return to the app</a>
        }
        @case ('error') {
          <p class="text-destructive">
            {{ error() }}
          </p>
          <p class="text-muted-foreground">Close this tab, then try Connect again in the app.</p>
        }
      }
    </main>
  `,
})
export class ConnectDesktopComponent implements OnInit {
  readonly #http = inject(HttpClient);
  readonly #route = inject(ActivatedRoute);

  protected readonly state = signal<'working' | 'redirect' | 'error'>('working');
  protected readonly error = signal('');
  protected readonly callbackUrl = signal('');

  ngOnInit(): void {
    const params = this.#route.snapshot.queryParamMap;
    const state = params.get('state');
    const port = params.get('port');
    if (!state || !port) {
      this.error.set('This link is incomplete. Start the connection from the desktop app.');

      return this.#fail();
    }
    const name = params.get('machine') || 'Desktop';
    this.#http.post<CreatedMachine>('/api/identity/machines', { name }).subscribe({
      next: (created) => {
        const target =
          `http://127.0.0.1:${encodeURIComponent(port)}/callback` +
          `?state=${encodeURIComponent(state)}&token=${encodeURIComponent(created.token)}`;
        this.callbackUrl.set(target);
        this.state.set('redirect');
        window.location.href = target;
      },
      error: () => {
        this.error.set('The machine token could not be created. Only the admin can connect a machine.');
        this.#fail();
      },
    });
  }

  #fail(): void {
    this.state.set('error');
  }
}
