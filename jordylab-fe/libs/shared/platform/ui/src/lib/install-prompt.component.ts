import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { InstallPromptStore } from '@jordylab-fe/shared/platform/api';

const BUTTON =
  'inline-flex h-12 min-w-0 flex-1 items-center justify-center rounded-xl text-[15px] font-semibold transition-colors';

/**
 * The one-time install sheet (spec 018 US1, FR-003/FR-006): Android offers the browser's own install prompt when it is
 * available (one tap), otherwise the browser-menu instructions; iOS keeps the Add-to-Home-Screen instructions because
 * Safari has no install event. The store owns prompting and dismissal, so this component only renders its state.
 */
@Component({
  selector: 'lib-install-prompt',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @switch (store.promptKind()) {
      @case ('android') {
        <div
          class="jordylab-install-dialog fixed inset-x-0 bottom-0 z-50 rounded-t-[18px] border-t border-input bg-card p-5 shadow-modal"
          role="dialog"
          aria-label="Install JordyLab"
        >
          <h2 class="text-xl font-semibold text-foreground">Install JordyLab</h2>
          @if (store.canPromptInstall()) {
            <p class="mt-1 text-[15px] text-muted-foreground">
              Add JordyLab to this phone's home screen — it opens full-screen, no app store needed.
            </p>
          } @else {
            <p class="mt-1 text-[15px] text-muted-foreground">
              Open the browser menu and choose "Install app" (or "Add to Home screen") to install JordyLab.
            </p>
          }
          <p class="mt-3 text-[14px] text-muted-foreground">
            Had the old Android app installed? Uninstall it so only one JordyLab icon remains.
          </p>
          <div class="mt-4 flex gap-3">
            @if (store.canPromptInstall()) {
              <button
                type="button"
                class="${BUTTON} bg-primary text-primary-foreground disabled:opacity-60"
                [disabled]="installing()"
                (click)="onInstall()"
              >
                Install app
              </button>
            }
            <button
              type="button"
              class="${BUTTON} border border-input text-foreground hover:bg-background"
              (click)="store.dismiss()"
            >
              {{ store.canPromptInstall() ? 'Not now' : 'Got it' }}
            </button>
          </div>
        </div>
      }
      @case ('ios') {
        <div
          class="jordylab-install-sheet fixed inset-x-0 bottom-0 z-50 rounded-t-[18px] border-t border-input bg-card p-5 shadow-modal"
          role="dialog"
          aria-label="Add JordyLab to Home Screen"
        >
          <h2 class="text-xl font-semibold text-foreground">Add JordyLab to your Home Screen</h2>
          <p class="mt-1 text-[15px] text-muted-foreground">
            Tap the Share button, then "Add to Home Screen" — JordyLab will open full-screen, just like an app.
          </p>
          <p class="mt-3 text-[14px] text-muted-foreground">
            Had the old Android app installed? Uninstall it so only one JordyLab icon remains.
          </p>
          <div class="mt-4 flex">
            <button
              type="button"
              class="${BUTTON} bg-primary text-primary-foreground"
              (click)="store.dismiss()"
            >
              Got it
            </button>
          </div>
        </div>
      }
    }
  `,
})
export class InstallPromptComponent {
  protected readonly store = inject(InstallPromptStore);
  /** The OS prompt can take a moment to open; a second tap while it is pending must do nothing. */
  protected readonly installing = signal(false);

  protected async onInstall(): Promise<void> {
    this.installing.set(true);
    try {
      await this.store.promptInstall();
    } finally {
      this.installing.set(false);
    }
  }
}
