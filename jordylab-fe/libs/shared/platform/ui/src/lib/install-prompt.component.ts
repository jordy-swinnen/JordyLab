import { Component, inject, input, output, ChangeDetectionStrategy } from '@angular/core';
import { InstallPromptStore } from '@jordylab-fe/shared/platform/api';

export type InstallDownloadStatus = 'idle' | 'preparing' | 'started' | 'failed';

const BUTTON =
  'inline-flex h-12 flex-1 items-center justify-center rounded-xl text-[15px] font-semibold transition-colors';

/**
 * Mounts the Android install dialog or the iOS Add-to-Home-Screen sheet — never both, never on
 * desktop, never inside the native app (spec FR-005, `InstallPromptStore.promptKind`).
 *
 * The parent owns the download itself and reports its progress through `status`, so the dialog
 * always answers a tap: "Preparing…", then "Download started" (or an error), never silence.
 */
@Component({
  selector: 'lib-install-prompt',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @switch (store.promptKind()) {
      @case ('android') {
        <div
          class="jordylab-install-dialog fixed inset-x-0 bottom-0 z-50 rounded-t-2xl border-t border-border bg-card p-5 shadow-2xl"
          role="dialog"
          aria-label="Get the JordyLab app"
        >
          <h2 class="text-xl font-semibold text-foreground">Get the JordyLab app</h2>
          <p class="mt-1 text-[15px] text-muted-foreground">
            Install JordyLab as a real app on this phone — no Play Store needed.
          </p>
          <ol class="mt-3 list-decimal space-y-1 pl-5 text-[15px] text-foreground">
            <li>Tap Download below.</li>
            <li>When your browser asks, allow installs from this browser.</li>
            <li>Open the downloaded file to install.</li>
          </ol>
          @switch (status()) {
            @case ('preparing') {
              <p class="mt-3 text-[14px] text-muted-foreground" role="status">Preparing the download…</p>
            }
            @case ('started') {
              <p class="mt-3 text-[14px] text-foreground" role="status">
                Download requested. When it finishes, open the file from your notifications or your Downloads folder.
              </p>
            }
            @case ('failed') {
              <p class="mt-3 text-[14px] text-destructive" role="alert">
                Could not prepare the download. Check your connection and try again.
              </p>
            }
          }
          <div class="mt-4 flex gap-3">
            <button
              type="button"
              class="${BUTTON} bg-primary text-primary-foreground disabled:opacity-60"
              [disabled]="status() === 'preparing'"
              (click)="download.emit()"
            >
              {{ status() === 'failed' ? 'Try again' : 'Download' }}
            </button>
            <button
              type="button"
              class="${BUTTON} border border-input text-foreground hover:bg-background"
              (click)="store.dismiss()"
            >
              Not now
            </button>
          </div>
        </div>
      }
      @case ('ios') {
        <div
          class="jordylab-install-sheet fixed inset-x-0 bottom-0 z-50 rounded-t-2xl border-t border-border bg-card p-5 shadow-2xl"
          role="dialog"
          aria-label="Add JordyLab to Home Screen"
        >
          <h2 class="text-xl font-semibold text-foreground">Add JordyLab to your Home Screen</h2>
          <p class="mt-1 text-[15px] text-muted-foreground">
            Tap the Share button, then "Add to Home Screen" — JordyLab will open full-screen, just like an app.
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

  readonly status = input<InstallDownloadStatus>('idle');
  readonly download = output<void>();
}
