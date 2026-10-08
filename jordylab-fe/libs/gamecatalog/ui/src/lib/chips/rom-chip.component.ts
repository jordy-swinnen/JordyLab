import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RomSummaryState } from '@jordylab-fe/gamecatalog/api';

const BASE =
  'inline-flex items-center gap-1.5 whitespace-nowrap rounded-[4px] px-2 py-[5px] font-mono text-[10.5px] font-medium uppercase leading-none tracking-[0.08em]';

interface RomLook {
  label: string;
  classes: string;
}

const LOOKS: Record<RomSummaryState, RomLook> = {
  VALIDATED: { label: 'Validated', classes: `${BASE} bg-[#9BE564] text-[#0B1A05]` },
  BROKEN: { label: 'Broken ROM', classes: `${BASE} bg-[#FF6B6B] text-[#2A0707]` },
  UNKNOWN: { label: 'Unknown', classes: `${BASE} border border-[#9A93AB] text-[#9A93AB]` },
  MIXED: { label: 'Mixed', classes: `${BASE} border border-[#F5B84A] text-[#F5B84A]` },
};

/**
 * Whether the ROM launches on a machine: Validated (lime, shield), Broken ROM (red, crossed circle), Unknown (grey outline,
 * question mark) or Mixed when machines disagree. Squared and icon-led so it is told apart from platform and status chips
 * without colour (spec 013 ui-design §3).
 */
@Component({
  selector: 'lib-rom-chip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'look().classes', '[attr.data-state]': 'state()' },
  template: `
    @switch (state()) {
      @case ('VALIDATED') {
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M12 3l8 3v6c0 4.5-3.2 8-8 9-4.8-1-8-4.5-8-9V6z" />
          <path d="M8.5 12l2.5 2.5 4.5-5" />
        </svg>
      }
      @case ('BROKEN') {
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true">
          <circle cx="12" cy="12" r="9" />
          <path d="M9 9l6 6M15 9l-6 6" />
        </svg>
      }
      @default {
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true">
          <circle cx="12" cy="12" r="9" />
          <path d="M9.6 9.4a2.5 2.5 0 1 1 3.6 2.2c-.8.4-1.2 1-1.2 1.9M12 16.8v.01" />
        </svg>
      }
    }
    {{ text() }}
  `,
})
export class RomChipComponent {
  state = input.required<RomSummaryState>();
  /** Overrides the label, e.g. "Validated on 1 of 2 hosts" for a mixed summary. */
  detail = input<string | null>(null);

  protected readonly look = computed(() => LOOKS[this.state()]);
  protected readonly text = computed(() => this.detail() ?? this.look().label);
}
