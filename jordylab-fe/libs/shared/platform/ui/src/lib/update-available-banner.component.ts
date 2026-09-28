import { Component, input, output, signal } from '@angular/core';

/** Dismissible "Update available" banner (spec US3-1, FR-011). */
@Component({
  selector: 'lib-update-available-banner',
  standalone: true,
  template: `
    @if (!dismissed()) {
      <div class="jordylab-update-banner" role="status">
        <p>Update available: v{{ versionName() }}</p>
        <p>{{ releaseNotes() }}</p>
        <button type="button" (click)="download.emit()">Update</button>
        <button type="button" (click)="dismissed.set(true)">Later</button>
      </div>
    }
  `,
})
export class UpdateAvailableBannerComponent {
  readonly versionName = input.required<string>();
  readonly releaseNotes = input.required<string>();
  readonly download = output<void>();

  protected readonly dismissed = signal(false);
}
