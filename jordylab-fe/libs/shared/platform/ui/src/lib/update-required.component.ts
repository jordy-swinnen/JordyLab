import { Component, input, output } from '@angular/core';

/**
 * Mandatory update screen (spec US3-2, FR-011) — no dismissal, no other UI reachable underneath.
 */
@Component({
  selector: 'lib-update-required',
  standalone: true,
  template: `
    <div class="jordylab-update-required" role="alertdialog" aria-label="Update required">
      <h2>Update required</h2>
      <p>This version of JordyLab is no longer supported. Install the latest version to continue.</p>
      <p>Latest version: {{ latestVersionName() }}</p>
      <button type="button" (click)="download.emit()">Update now</button>
    </div>
  `,
})
export class UpdateRequiredComponent {
  readonly latestVersionName = input.required<string>();
  readonly download = output<void>();
}
