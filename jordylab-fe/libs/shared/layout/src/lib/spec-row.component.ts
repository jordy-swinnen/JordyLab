import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** One fact about a thing: a small label at the left and its value, projected, at the right. */
@Component({
  selector: 'lib-spec-row',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-baseline justify-between gap-4 border-b border-border py-3.5 last:border-b-0">
      <span class="label">{{ label() }}</span>
      <span class="text-right text-[15px] font-medium text-foreground"><ng-content /></span>
    </div>
  `,
})
export class SpecRowComponent {
  label = input.required<string>();
}
