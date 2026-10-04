import { Component, inject, signal } from '@angular/core';
import { BiometricUnlockService } from './biometric-unlock.service';

/**
 * "Unlock with fingerprint" setting (spec 007 US4), shown on the app's native-only Settings → App page.
 * It always renders once mounted and says plainly when the phone has no usable biometrics. A pure
 * signal-reading component — `available`/`enabled` live on the service, not here.
 */
@Component({
  selector: 'lib-biometric-unlock-toggle',
  standalone: true,
  template: `
    <section class="rounded-2xl border border-border p-5" aria-labelledby="fingerprint-heading">
      <div class="flex items-start justify-between gap-4">
        <div class="min-w-0 flex-1">
          <h3 id="fingerprint-heading" class="text-base font-semibold">Unlock with fingerprint</h3>
          <p class="mt-1 text-sm text-muted-foreground">
            @if (available()) {
              Open JordyLab with your fingerprint instead of typing your password. Turning this off, or signing out, forgets
              the stored session on this phone.
            } @else {
              This phone has no fingerprint (or other biometric) set up, so unlock is not available.
            }
          </p>
        </div>
        <button
          type="button"
          role="switch"
          aria-labelledby="fingerprint-heading"
          [attr.aria-checked]="enabled()"
          [disabled]="!available() || busy()"
          (click)="onToggle()"
          class="relative mt-0.5 inline-flex h-7 w-12 flex-shrink-0 items-center rounded-full border border-input transition-colors disabled:opacity-50"
          [class]="enabled() ? 'bg-primary' : 'bg-card'"
        >
          <span
            class="inline-block h-5 w-5 rounded-full bg-foreground transition-transform"
            [class]="enabled() ? 'translate-x-6' : 'translate-x-1'"
          ></span>
        </button>
      </div>
      @if (failure()) {
        <p class="mt-3 text-sm text-destructive" role="alert">{{ failure() }}</p>
      }
    </section>
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

  protected async onToggle(): Promise<void> {
    this.busy.set(true);
    this.failure.set(null);
    try {
      if (this.enabled()) {
        await this.#biometric.disable();
      } else if (!(await this.#biometric.enable())) {
        this.failure.set('Could not turn on fingerprint unlock. Check that a fingerprint is enrolled, then try again.');
      }
    } finally {
      this.busy.set(false);
    }
  }
}
