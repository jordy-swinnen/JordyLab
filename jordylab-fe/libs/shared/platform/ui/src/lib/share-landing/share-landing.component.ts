import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '@jordylab-fe/shared/auth';
import { ShareTargetService } from '@jordylab-fe/shared/platform/api';

/**
 * Share-landing screen (spec US5, contracts/app-shell-contract.md) — reached via
 * `ShareTargetService`'s `shareReceived` navigation. Role-filtered destinations: "Ask the
 * catalog" for everyone, "Save to FNA" for the admin only (FR-014/FR-017, research D6). Sits
 * behind `authGuard` on its route, so an unauthenticated share arrival naturally goes through
 * login first — the payload stays in {@link ShareTargetService}'s in-memory signal throughout,
 * never persisted (spec US5-4).
 */
@Component({
  selector: 'lib-share-landing',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @if (sharedText(); as text) {
      <div class="jordylab-share-landing">
        <h2>Share to JordyLab</h2>
        <p class="jordylab-share-landing__text">{{ text }}</p>

        <button type="button" [disabled]="submitting()" (click)="onAskCatalog(text)">
          Ask the catalog
        </button>

        @if (isAdmin()) {
          <button type="button" [disabled]="submitting()" (click)="onSaveToFna(text)">
            Save to FNA
          </button>
        }

        @if (error()) {
          <p class="jordylab-share-landing__error" role="alert">{{ error() }}</p>
        }
      </div>
    }
  `,
})
export class ShareLandingComponent {
  readonly #shareTarget = inject(ShareTargetService);
  readonly #auth = inject(AuthService);
  readonly #router = inject(Router);

  protected readonly isAdmin = this.#auth.isAdmin;
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly sharedText = computed(() => {
    const share = this.#shareTarget.pendingShare();

    return share?.texts[0] ?? null;
  });

  protected onAskCatalog(text: string): void {
    this.#shareTarget.clear();
    void this.#router.navigate(['/games/chat'], { queryParams: { prefill: text } });
  }

  protected async onSaveToFna(text: string): Promise<void> {
    this.submitting.set(true);
    this.error.set(null);
    try {
      await this.#shareTarget.submitToFna(text);
      this.#shareTarget.clear();
      void this.#router.navigateByUrl('/fna/articles');
    } catch {
      this.error.set('Could not save to FNA. Try again.');
    } finally {
      this.submitting.set(false);
    }
  }
}
