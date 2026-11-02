import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

/** A failed load, said in plain words, with a way to try again. The page title stays on screen above it. */
@Component({
  selector: 'lib-error-state',
  standalone: true,
  imports: [HlmButtonDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      role="alert"
      class="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-destructive px-[18px] py-4 text-[14.5px] text-destructive"
    >
      <span>{{ message() }}</span>
      <button hlmBtn variant="outline" type="button" class="h-11 rounded-[10px] px-4 text-sm font-semibold" (click)="retry.emit()">
        Try again
      </button>
    </div>
  `,
})
export class ErrorStateComponent {
  message = input.required<string>();

  retry = output<void>();
}
