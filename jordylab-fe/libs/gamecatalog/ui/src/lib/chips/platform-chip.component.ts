import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { PlatformChip } from '@jordylab-fe/gamecatalog/api';

/**
 * A platform name in the colours the server chose for it (official brand colours, contrast-checked, spec 013 FR-040).
 * The front end holds no colour table of its own: what arrives is what is painted.
 */
@Component({
  selector: 'lib-platform-chip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class:
      'inline-flex items-center rounded-[7px] px-2 py-[5px] font-mono text-[10.5px] font-medium uppercase tracking-[0.08em] leading-none whitespace-nowrap',
    '[style.background]': 'chip().background',
    '[style.color]': 'chip().foreground',
    '[style.border]': 'border()',
  },
  template: '{{ chip().name }}',
})
export class PlatformChipComponent {
  chip = input.required<PlatformChip>();

  protected readonly border = computed(() => {
    const border = this.chip().border;

    return border ? `1px solid ${border}` : '1px solid transparent';
  });
}
