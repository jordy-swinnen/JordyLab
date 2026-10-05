import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';

/**
 * Mandatory update screen (spec US3-2, FR-011) — no dismissal, no other UI reachable underneath.
 */
@Component({
  selector: 'lib-update-required',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div
      class="jordylab-update-required flex min-h-screen flex-col items-center justify-center gap-3 bg-background p-8 text-center"
      role="alertdialog"
      aria-label="Update required"
    >
      <h2 class="text-2xl font-semibold text-foreground">Update required</h2>
      <p class="max-w-sm text-[15px] text-muted-foreground">
        This version of JordyLab is no longer supported. Install the latest version to continue.
      </p>
      <p class="text-[14px] text-muted-foreground">Latest version: {{ latestVersionName() }}</p>
      <button
        type="button"
        class="mt-2 inline-flex h-12 items-center justify-center rounded-xl bg-primary px-8 text-[15px] font-semibold text-primary-foreground"
        (click)="download.emit()"
      >
        Update now
      </button>
    </div>
  `,
})
export class UpdateRequiredComponent {
  readonly latestVersionName = input.required<string>();
  readonly download = output<void>();
}
