import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  model,
  output,
  viewChild,
} from '@angular/core';
import { GameSort, SORT_LABELS } from '@jordylab-fe/gamecatalog/api';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';

/**
 * The only two things always on the library: the search box and one Filters button (with a count of what is in use) next
 * to the sort. The panel itself is projected in, so it sits right under the button and the bar can return focus to it.
 */
@Component({
  selector: 'lib-filter-bar',
  standalone: true,
  imports: [HlmInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './filter-bar.component.html',
  host: { '(keydown.escape)': 'closeFromKeyboard($event)' },
})
export class FilterBarComponent {
  protected readonly filtersButton = viewChild.required<ElementRef<HTMLButtonElement>>('filtersButton');

  searchTerm = input.required<string>();
  activeCount = input.required<number>();
  sort = input.required<GameSort>();
  open = model(false);

  searchChange = output<string>();
  sortChange = output<GameSort>();

  protected readonly sorts = (Object.keys(SORT_LABELS) as GameSort[]).map((value) => ({
    value,
    label: SORT_LABELS[value],
  }));

  focusFiltersButton(): void {
    this.filtersButton().nativeElement.focus();
  }

  protected onSearchInput(event: Event): void {
    this.searchChange.emit((event.target as HTMLInputElement).value);
  }

  protected onSortChange(event: Event): void {
    this.sortChange.emit((event.target as HTMLSelectElement).value as GameSort);
  }

  protected toggle(): void {
    this.open.update((isOpen) => !isOpen);
  }

  protected closeFromKeyboard(event: Event): void {
    if (!this.open()) {
      return;
    }
    event.stopPropagation();
    this.open.set(false);
    this.focusFiltersButton();
  }
}
