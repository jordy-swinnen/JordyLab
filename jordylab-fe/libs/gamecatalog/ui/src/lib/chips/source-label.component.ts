import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { GameSource } from '@jordylab-fe/gamecatalog/api';
import { SOURCE_LABELS } from '../source-labels';

/** Where a game comes from: Steam (Owned), Steam (Family), Emulated or Console. Plain text, so it never competes with the brand chips. */
@Component({
  selector: 'lib-source-label',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class:
      'inline-flex items-center whitespace-nowrap font-mono text-[10.5px] font-medium uppercase tracking-[0.1em] text-secondary-foreground',
  },
  template: '{{ label() }}',
})
export class SourceLabelComponent {
  source = input.required<GameSource>();

  protected readonly label = computed(() => SOURCE_LABELS[this.source()]);
}
