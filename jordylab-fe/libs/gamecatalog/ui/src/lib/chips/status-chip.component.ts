import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { InstallStatus } from '@jordylab-fe/gamecatalog/api';

const BASE =
  'inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border-[1.5px] px-2.5 py-1 text-xs font-semibold leading-none';

/**
 * Installed (teal, check) and Not installed (amber, dashed ring and dashed border). The colour is never the only signal:
 * the icon and the border style differ too (spec 013 FR-041, ui-design §3.2).
 */
@Component({
  selector: 'lib-status-chip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'chipClass()', '[attr.data-status]': 'status()' },
  template: `
    @if (installed()) {
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M5 12.5l4.5 4.5L19 7.5" />
      </svg>
      Installed
    } @else {
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-dasharray="3.4 3" aria-hidden="true">
        <circle cx="12" cy="12" r="8" />
      </svg>
      Not installed
    }
  `,
})
export class StatusChipComponent {
  status = input.required<Exclude<InstallStatus, 'ALL'>>();

  protected readonly installed = computed(() => this.status() === 'INSTALLED');
  protected readonly chipClass = computed(() =>
    this.installed()
      ? `${BASE} border-solid border-[#2DD4BF] text-[#2DD4BF]`
      : `${BASE} border-dashed border-[#F5B84A] text-[#F5B84A]`,
  );
}
