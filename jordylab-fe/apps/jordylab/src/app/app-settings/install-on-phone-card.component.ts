import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import {
  InstallPromptStore,
  isRunningStandalone,
  PlatformService,
} from '@jordylab-fe/shared/platform/api';

const BUTTON =
  'inline-flex h-11 items-center justify-center rounded-xl bg-primary px-5 text-[15px] font-semibold text-primary-foreground disabled:opacity-60';

/**
 * Settings → App: the permanent install entry (spec 018 FR-005) that replaced the fingerprint section. On a phone
 * browser that is not already running the installed app it re-offers the browser's install prompt, or the browser-menu
 * instructions when the browser has none, plus the note about removing the old sideloaded Android app.
 */
@Component({
  selector: 'app-install-on-phone-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @if (visible()) {
      <section class="mt-10 flex max-w-2xl flex-col gap-4" data-testid="install-on-phone">
        <h3 class="section-title">Install on this phone</h3>
        @if (store.canPromptInstall()) {
          <p class="text-[15px] text-muted-foreground">
            Add JordyLab to this phone's home screen — it opens full-screen, no app store needed.
          </p>
          <div class="flex">
            <button type="button" class="${BUTTON}" [disabled]="installing()" (click)="onInstall()">
              Install app
            </button>
          </div>
        } @else {
          <ol class="list-decimal space-y-1 pl-5 text-[15px] text-foreground">
            <li>Open the browser menu.</li>
            <li>Choose "Install app" (Android) or "Add to Home screen" (iPhone/iPad).</li>
            <li>Confirm — JordyLab then opens full-screen like an app.</li>
          </ol>
        }
        <p class="text-[13px] text-muted-foreground">
          Had the old Android app installed? Uninstall it so only one JordyLab icon remains.
        </p>
      </section>
    }
  `,
})
export class InstallOnPhoneCardComponent {
  protected readonly store = inject(InstallPromptStore);
  protected readonly installing = signal(false);

  readonly #platform = inject(PlatformService);
  protected readonly visible = computed(() => {
    const platform = this.#platform.platform();

    return (platform === 'web-android' || platform === 'web-ios') && !isRunningStandalone();
  });

  protected async onInstall(): Promise<void> {
    this.installing.set(true);
    try {
      await this.store.promptInstall();
    } finally {
      this.installing.set(false);
    }
  }
}
