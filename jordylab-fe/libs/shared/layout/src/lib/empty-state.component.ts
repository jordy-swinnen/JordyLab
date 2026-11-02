import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

/** What a screen shows when it has nothing to list: what is missing, why, and at most one action that fixes it. */
@Component({
  selector: 'lib-empty-state',
  standalone: true,
  imports: [HlmButtonDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="flex flex-col items-center justify-center gap-2 rounded-[14px] border border-dashed border-input px-6 py-14 text-center md:py-16"
    >
      <p class="text-[17px] font-semibold text-foreground">{{ title() }}</p>
      <p class="max-w-[44ch] text-[14.5px] leading-normal text-secondary-foreground">{{ hint() }}</p>
      @if (actionLabel()) {
        <button
          hlmBtn
          type="button"
          class="mt-1.5 h-11 rounded-[10px] px-[18px] text-[14.5px] font-semibold"
          [variant]="actionKind() === 'primary' ? 'default' : 'outline'"
          (click)="actionClicked.emit()"
        >
          {{ actionLabel() }}
        </button>
      }
    </div>
  `,
})
export class EmptyStateComponent {
  title = input.required<string>();
  hint = input.required<string>();
  actionLabel = input('');
  actionKind = input<'primary' | 'secondary'>('secondary');

  actionClicked = output<void>();
}
