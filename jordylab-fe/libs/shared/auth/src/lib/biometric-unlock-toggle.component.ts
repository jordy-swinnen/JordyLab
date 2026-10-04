import { Component, inject, signal } from '@angular/core';
import { BiometricUnlockService } from './biometric-unlock.service';

/**
 * "Unlock with fingerprint" toggle (spec US4). Native-only — the app shell decides whether to
 * mount this at all (same pattern as the rest of `libs/shared/platform`'s platform-conditional
 * UI, e.g. the desktop QR entry): it always renders once mounted, but hides itself once
 * {@link BiometricUnlockService.available} resolves `false` (no biometric hardware/enrollment).
 * A pure signal-reading component — `available`/`enabled` live on the service, not here.
 *
 * **Implementation-time discovery**: the original task referenced extending a `UserMenuComponent`
 * from spec 006 — no such component exists in this codebase (the sign-out UI is inlined directly
 * in `apps/jordylab/src/app/app.html`). This is a small standalone component instead, mounted
 * next to that same sign-out block.
 */
@Component({
  selector: 'lib-biometric-unlock-toggle',
  standalone: true,
  template: `
    @if (available()) {
      <label class="flex items-center gap-2 text-sm text-secondary-foreground">
        <input
          type="checkbox"
          [checked]="enabled()"
          [disabled]="busy()"
          (change)="onToggle($event)"
        />
        Unlock with fingerprint
      </label>
      @if (failure()) {
        <p class="mt-1 text-xs text-destructive" role="alert">{{ failure() }}</p>
      }
    }
  `,
})
export class BiometricUnlockToggleComponent {
  readonly #biometric = inject(BiometricUnlockService);

  protected readonly available = this.#biometric.available;
  protected readonly enabled = this.#biometric.enabled;
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);

  constructor() {
    void this.#biometric.refresh();
  }

  protected async onToggle(event: Event): Promise<void> {
    const checkbox = event.target as HTMLInputElement;
    this.busy.set(true);
    this.failure.set(null);
    try {
      if (this.enabled()) {
        await this.#biometric.disable();
      } else if (!(await this.#biometric.enable())) {
        this.failure.set('Could not turn on fingerprint unlock. Check that a fingerprint is enrolled, then try again.');
      }
    } finally {
      // The browser flips the box on click; put it back in step with the real state when enabling failed.
      checkbox.checked = this.enabled();
      this.busy.set(false);
    }
  }
}
