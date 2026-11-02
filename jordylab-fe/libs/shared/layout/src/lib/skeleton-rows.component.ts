import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';

/** Placeholder blocks the shape of the rows that are about to load. Pass a height that matches the real row. */
@Component({
  selector: 'lib-skeleton-rows',
  standalone: true,
  imports: [HlmSkeletonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-2.5" aria-hidden="true">
      @for (row of rows(); track row) {
        <hlm-skeleton class="w-full rounded-xl" [style.height.px]="height()" />
      }
    </div>
  `,
})
export class SkeletonRowsComponent {
  count = input(4);
  height = input(84);

  protected readonly rows = computed(() => Array.from({ length: this.count() }, (_, index) => index));
}
