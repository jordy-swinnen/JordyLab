import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { BiometricUnlockService } from './biometric-unlock.service';

/**
 * "Unlock with fingerprint" setting (spec 007 US4), shown on the app's native-only Settings → App page.
 * It always renders once mounted and says plainly when the phone has no usable biometrics. A pure
 * signal-reading component — `available`/`enabled` live on the service, not here.
 */
@Component({
  selector: 'lib-biometric-unlock-toggle',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <section class="panel p-5" aria-labelledby="fingerprint-heading">
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
          class="inline-flex h-11 w-[52px] flex-shrink-0 cursor-pointer items-center justify-center rounded-full outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-default disabled:opacity-50"
        >
          <span class="relative block h-[27px] w-[46px] rounded-full transition-colors duration-200" [class]="enabled() ? 'bg-primary' : 'bg-input'">
            <span
              class="absolute left-[3px] top-[3px] block h-[21px] w-[21px] rounded-full transition-transform duration-200"
              [class]="enabled() ? 'translate-x-[19px] bg-primary-foreground' : 'bg-foreground'"
            ></span>
          </span>
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
