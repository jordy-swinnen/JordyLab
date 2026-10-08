import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ActiveFilter } from '@jordylab-fe/gamecatalog/api';

/**
 * Every filter in use as a removable chip, "Clear all" and the live result count. Shown as soon as anything is active, so
 * nothing that narrows the library is ever hidden.
 */
@Component({
  selector: 'lib-active-filters',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './active-filters.component.html',
})
export class ActiveFiltersComponent {
  filters = input.required<ActiveFilter[]>();
  totalElements = input.required<number>();
  unknownPlayerCount = input<number | null>(null);

  clearAll = output<void>();

  protected readonly showClearAll = computed(() => this.filters().length > 1);
}
