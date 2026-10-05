import { Component, input, output, signal, ChangeDetectionStrategy } from '@angular/core';

/** Dismissible "Update available" banner (spec US3-1, FR-011). */
@Component({
  selector: 'lib-update-available-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @if (!dismissed()) {
      <div
        class="jordylab-update-banner fixed inset-x-0 bottom-0 z-50 border-t border-border bg-card p-4 shadow-2xl"
        role="status"
      >
        <p class="text-[15px] font-semibold text-foreground">Update available: v{{ versionName() }}</p>
        <p class="mt-1 text-[14px] text-muted-foreground">{{ releaseNotes() }}</p>
        <div class="mt-3 flex gap-3">
          <button
            type="button"
            class="inline-flex h-11 flex-1 items-center justify-center rounded-xl bg-primary text-[15px] font-semibold text-primary-foreground"
            (click)="download.emit()"
          >
            Update
          </button>
          <button
            type="button"
            class="inline-flex h-11 flex-1 items-center justify-center rounded-xl border border-input text-[15px] text-foreground"
            (click)="dismissed.set(true)"
          >
            Later
          </button>
        </div>
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
