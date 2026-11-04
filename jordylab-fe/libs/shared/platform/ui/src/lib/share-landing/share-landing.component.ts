import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '@jordylab-fe/shared/auth';
import { firstValueFrom } from 'rxjs';

const BUTTON =
  'inline-flex h-11 items-center justify-center rounded-xl px-5 text-[15px] font-semibold transition-colors';

/**
 * The Web Share Target landing screen (spec 018 US2, contracts/share-target-contract.md): an installed Android PWA is
 * opened here by the OS share sheet with `?title&text&url`. The payload lives only in the URL — no service and no
 * storage — so a share that needs sign-in survives the sign-in round trip through the login page's `returnUrl`, and a
 * killed tab drops the share exactly like the OS would.
 */
@Component({
  selector: 'lib-share-landing',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="jordylab-share-landing max-w-2xl">
      @if (hasPayload()) {
        <h2 class="text-[22px] font-semibold text-foreground">Share to JordyLab</h2>
        @if (sharedTitle(); as title) {
          <p class="mt-1 text-[15px] font-medium text-foreground">{{ title }}</p>
        }
        <p class="jordylab-share-landing__text mt-2 text-[15px] break-words text-secondary-foreground">
          {{ sharedText() }}
        </p>

        <div class="mt-5 flex flex-wrap gap-3">
          <button
            type="button"
            class="${BUTTON} bg-primary text-primary-foreground"
            (click)="onAskLibBot()"
          >
            Ask LibBot
          </button>

          @if (isAdmin()) {
            <button
              type="button"
              class="${BUTTON} border border-input text-foreground hover:bg-background disabled:opacity-60"
              [disabled]="submitting() || !targetUrl()"
              (click)="onSaveToFna()"
            >
              Save to FNA
            </button>
          }
        </div>

        @if (isAdmin() && !targetUrl()) {
          <p class="mt-3 text-[14px] text-muted-foreground">
            Save to FNA needs a link — this share has no web address in it.
          </p>
        }

        @if (error()) {
          <p class="jordylab-share-landing__error mt-3 text-[14px] font-medium text-destructive" role="alert">
            {{ error() }}
          </p>
        }
      } @else {
        <h2 class="text-[22px] font-semibold text-foreground">Nothing was shared</h2>
        <p class="mt-2 text-[15px] text-secondary-foreground">
          Share a link or some text to JordyLab from another app, or open the Library instead.
        </p>
        <div class="mt-5 flex">
          <a routerLink="/games/grid" class="${BUTTON} bg-primary text-primary-foreground">Open the Library</a>
        </div>
      }
    </div>
  `,
})
export class ShareLandingComponent {
  readonly #route = inject(ActivatedRoute);
  readonly #router = inject(Router);
  readonly #http = inject(HttpClient);

  protected readonly isAdmin = inject(AuthService).isAdmin;
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  // Reactive: a second share into the already-open app changes the query params of the same route.
  readonly #params = toSignal(this.#route.queryParamMap, { initialValue: this.#route.snapshot.queryParamMap });

  protected readonly sharedTitle = computed(() => this.#params().get('title')?.trim() || null);

  protected readonly sharedText = computed(() => {
    const text = this.#params().get('text')?.trim();

    return text || this.#params().get('url')?.trim() || null;
  });

  protected readonly targetUrl = computed(() =>
    targetUrlFrom(this.#params().get('url'), this.#params().get('text')),
  );

  protected readonly hasPayload = computed(() => Boolean(this.sharedTitle() || this.sharedText()));

  protected onAskLibBot(): void {
    void this.#router.navigate(['/games/libbot'], { queryParams: { prefill: this.sharedText() ?? '' } });
  }

  protected async onSaveToFna(): Promise<void> {
    const url = this.targetUrl();
    if (!url) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.#http.post('/api/fna/articles/manual', { url }));
      void this.#router.navigateByUrl('/fna/articles');
    } catch {
      this.error.set('Could not save to FNA. Try again.');
    } finally {
      this.submitting.set(false);
    }
  }
}

/**
 * The link to save: the `url` parameter when it is one, else the first `http(s)` address inside `text` — Android apps
 * commonly put the shared URL at the end of the text (spec 018 edge cases). Trailing sentence punctuation is trimmed.
 */
export function targetUrlFrom(urlParameter: string | null, textParameter: string | null): string | null {
  const direct = urlParameter?.trim();
  if (direct && /^https?:\/\//i.test(direct)) {
    return direct;
  }
  const match = /https?:\/\/[^\s"'<>]+/i.exec(textParameter ?? '');
  if (!match) {
    return null;
  }

  return match[0].replace(/[.,;:!?)\]]+$/, '');
}
