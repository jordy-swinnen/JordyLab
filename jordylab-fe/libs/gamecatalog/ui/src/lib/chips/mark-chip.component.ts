import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MARK_LABELS, MarkType } from '@jordylab-fe/gamecatalog/api';

const BASE =
  'inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold leading-none';

const TINTS: Record<MarkType, string> = {
  WANT_TO_PLAY: 'bg-[#A99BFF]/15 text-[#A99BFF]',
  PLAYED_LIKED: 'bg-[#FF8FB1]/15 text-[#FF8FB1]',
  PLAYED_DISLIKED: 'bg-[#98A2B3]/15 text-[#98A2B3]',
};

const OUTLINES: Record<MarkType, string> = {
  WANT_TO_PLAY: 'ring-[1.5px] ring-inset ring-[#A99BFF]',
  PLAYED_LIKED: 'ring-[1.5px] ring-inset ring-[#FF8FB1]',
  PLAYED_DISLIKED: 'ring-[1.5px] ring-inset ring-[#98A2B3]',
};

/**
 * One of the three marks as a soft pill with its public total. Your own vote gets an outline, so it is never colour alone.
 * With {@code compact} only the icon and the count show (the vote rail on a cover); the full text stays for screen readers.
 */
@Component({
  selector: 'lib-mark-chip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': 'chipClass()',
    '[attr.data-mark]': 'mark()',
    '[attr.data-mine]': 'mine()',
    '[attr.aria-label]': 'description()',
    role: 'img',
  },
  template: `
    @switch (mark()) {
      @case ('WANT_TO_PLAY') {
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M6 3h12v18l-6-4.5L6 21z" />
        </svg>
      }
      @case ('PLAYED_LIKED') {
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M7 10v10H3V10zM7 10l4-7c1.7 0 2.5 1.2 2 3l-.7 3H19a2 2 0 0 1 2 2.4l-1.4 6.6A2 2 0 0 1 17.7 20H7" />
        </svg>
      }
      @case ('PLAYED_DISLIKED') {
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M17 14V4h4v10zM17 14l-4 7c-1.7 0-2.5-1.2-2-3l.7-3H5a2 2 0 0 1-2-2.4L4.4 6A2 2 0 0 1 6.3 4H17" />
        </svg>
      }
    }
    @if (!compact()) {
      <span aria-hidden="true">{{ label() }}</span>
    }
    @if (count() !== null) {
      <span class="font-mono" aria-hidden="true">{{ count() }}</span>
    }
  `,
})
export class MarkChipComponent {
  mark = input.required<MarkType>();
  count = input<number | null>(null);
  /** True when this is the signed-in person's own vote. */
  mine = input(false);
  compact = input(false);

  protected readonly label = computed(() => MARK_LABELS[this.mark()]);
  protected readonly chipClass = computed(
    () => `${BASE} ${TINTS[this.mark()]} ${this.mine() ? OUTLINES[this.mark()] : ''}`,
  );
  protected readonly description = computed(() => {
    const count = this.count();
    const label = this.label();
    const mine = this.mine() ? ' (your mark)' : '';

    return count === null ? `${label}${mine}` : `${label}: ${count}${mine}`;
  });
}
