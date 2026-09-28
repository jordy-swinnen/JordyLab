import { Component, inject, signal } from '@angular/core';
import { BiometricUnlockService } from './biometric-unlock.service';

/**
 * "Unlock with fingerprint" toggle (spec US4). Native-only — the app shell decides whether to
 * mount this at all (same pattern as the rest of `libs/shared/platform`'s platform-conditional
 * UI, e.g. the desktop QR entry): it always renders once mounted, but hides itself once
 * {@link BiometricUnlockService.isAvailable} resolves `false` (no biometric hardware/enrollment).
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
          (change)="onToggle()"
        />
        Unlock with fingerprint
      </label>
    }
  `,
})
export class BiometricUnlockToggleComponent {
  readonly #biometric = inject(BiometricUnlockService);

  protected readonly available = signal(false);
  protected readonly enabled = signal(false);
  protected readonly busy = signal(false);

  constructor() {
    void this.#refreshState();
  }

  protected async onToggle(): Promise<void> {
    this.busy.set(true);
    try {
      if (this.enabled()) {
        await this.#biometric.disable();
        this.enabled.set(false);
      } else {
        const succeeded = await this.#biometric.enable();
        this.enabled.set(succeeded);
      }
    } finally {
      this.busy.set(false);
    }
  }

  async #refreshState(): Promise<void> {
    const [available, enabled] = await Promise.all([
      this.#biometric.isAvailable(),
      this.#biometric.isEnabled(),
    ]);
    this.available.set(available);
    this.enabled.set(enabled);
  }
}
