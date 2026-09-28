import { Component, inject, output } from '@angular/core';
import { InstallPromptStore } from '@jordylab-fe/shared/platform/api';

/**
 * Mounts the Android install dialog or the iOS Add-to-Home-Screen sheet — never both, never on
 * desktop, never inside the native app (spec FR-005, `InstallPromptStore.promptKind`).
 */
@Component({
  selector: 'lib-install-prompt',
  standalone: true,
  template: `
    @switch (store.promptKind()) {
      @case ('android') {
        <div class="jordylab-install-dialog" role="dialog" aria-label="Get the JordyLab app">
          <h2>Get the JordyLab app</h2>
          <p>Install JordyLab as a real app on this phone — no Play Store needed.</p>
          <ol>
            <li>Tap Download below.</li>
            <li>When your browser asks, allow installs from this browser.</li>
            <li>Open the downloaded file to install.</li>
          </ol>
          <button type="button" (click)="download.emit()">Download</button>
          <button type="button" (click)="store.dismiss()">Not now</button>
        </div>
      }
      @case ('ios') {
        <div class="jordylab-install-sheet" role="dialog" aria-label="Add JordyLab to Home Screen">
          <h2>Add JordyLab to your Home Screen</h2>
          <p>Tap the Share button, then "Add to Home Screen" — JordyLab will open full-screen, just like an app.</p>
          <button type="button" (click)="store.dismiss()">Got it</button>
        </div>
      }
    }
  `,
})
export class InstallPromptComponent {
  protected readonly store = inject(InstallPromptStore);

  readonly download = output<void>();
}
