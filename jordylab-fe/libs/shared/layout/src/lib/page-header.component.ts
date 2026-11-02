import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** The start of every page: the title, an optional one-line subtitle, and the page's actions projected at the right. */
@Component({
  selector: 'lib-page-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div class="flex flex-col gap-2">
        <h2 class="page-title">{{ title() }}</h2>
        @if (subtitle()) {
          <p class="text-[15px] text-secondary-foreground">{{ subtitle() }}</p>
        }
      </div>
      <ng-content />
    </div>
  `,
})
export class PageHeaderComponent {
  title = input.required<string>();
  subtitle = input('');
}
