import { afterNextRender, ChangeDetectionStrategy, Component, ElementRef, inject, input, output } from '@angular/core';
import {
  GameSort,
  GameSource,
  INSTALL_STATUS_LABELS,
  InstallStatus,
  MARK_LABELS,
  MarkScope,
  MarkType,
  MIN_LOCAL_PLAYERS_CEILING,
  MIN_LOCAL_PLAYERS_FLOOR,
  PlaceOption,
  PlatformChip,
  ROM_STATUS_LABELS,
  RomStatus,
  SORT_LABELS,
} from '@jordylab-fe/gamecatalog/api';
import { SOURCE_LABELS, SOURCE_ORDER } from '../source-labels';

interface Option<T> {
  value: T;
  label: string;
}

function optionsOf<T extends string>(labels: Record<T, string>, order: readonly T[]): Option<T>[] {
  return order.map((value) => ({ value, label: labels[value] }));
}

/**
 * Everything a person can choose to narrow the library, in one panel: a popover under the Filters button on a wide screen
 * and a bottom sheet on a phone. The panel only ever offers what exists (platforms and places come from the library).
 */
@Component({
  selector: 'lib-filters-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './filters-panel.component.html',
})
export class FiltersPanelComponent {
  readonly #host = inject<ElementRef<HTMLElement>>(ElementRef);

  platforms = input.required<PlatformChip[]>();
  places = input.required<PlaceOption[]>();
  selectedPlatforms = input.required<string[]>();
  selectedPlaces = input.required<string[]>();
  installStatus = input.required<InstallStatus>();
  selectedSources = input.required<GameSource[]>();
  minLocalPlayers = input.required<number | null>();
  selectedRomStatuses = input.required<RomStatus[]>();
  selectedMarks = input.required<MarkType[]>();
  markScope = input.required<MarkScope>();
  sort = input.required<GameSort>();
  resultCount = input.required<number>();
  hasFilters = input.required<boolean>();

  platformToggle = output<string>();
  placeToggle = output<string>();
  installStatusChange = output<InstallStatus>();
  sourceToggle = output<GameSource>();
  minLocalPlayersChange = output<number | null>();
  romStatusToggle = output<RomStatus>();
  markToggle = output<MarkType>();
  markScopeChange = output<MarkScope>();
  sortChange = output<GameSort>();
  clearAll = output<void>();
  done = output<void>();

  protected readonly installStatuses = optionsOf<InstallStatus>(INSTALL_STATUS_LABELS, [
    'INSTALLED',
    'NOT_INSTALLED',
    'ALL',
  ]);
  protected readonly sources = optionsOf<GameSource>(SOURCE_LABELS, SOURCE_ORDER);
  protected readonly romStatuses = optionsOf<RomStatus>(ROM_STATUS_LABELS, ['UNKNOWN', 'VALIDATED', 'BROKEN']);
  protected readonly marks = optionsOf<MarkType>(MARK_LABELS, ['WANT_TO_PLAY', 'PLAYED_LIKED', 'PLAYED_DISLIKED']);
  protected readonly sorts = optionsOf<GameSort>(SORT_LABELS, ['TITLE', 'MOST_WANTED', 'MOST_LIKED']);
  protected readonly markScopes: Option<MarkScope>[] = [
    { value: 'ALL', label: "Anyone's" },
    { value: 'MINE', label: 'Only mine' },
  ];

  protected readonly optionClass =
    'inline-flex min-h-10 cursor-pointer items-center gap-2 rounded-full border border-input px-3.5 text-[13.5px] font-semibold text-secondary-foreground outline-none hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary aria-pressed:border-transparent aria-pressed:bg-foreground aria-pressed:text-background';
  protected readonly groupHeadingClass =
    'mb-2.5 font-mono text-[10.5px] font-medium uppercase tracking-[0.14em] text-muted-foreground';

  constructor() {
    afterNextRender(() => this.#host.nativeElement.querySelector<HTMLElement>('button')?.focus());
  }

  protected playersLabel(): string {
    const players = this.minLocalPlayers();

    return players ? `${players}+` : 'Any';
  }

  protected fewerPlayers(): void {
    const players = this.minLocalPlayers();
    this.minLocalPlayersChange.emit(!players || players <= MIN_LOCAL_PLAYERS_FLOOR ? null : players - 1);
  }

  protected morePlayers(): void {
    const players = this.minLocalPlayers();
    this.minLocalPlayersChange.emit(
      players ? Math.min(players + 1, MIN_LOCAL_PLAYERS_CEILING) : MIN_LOCAL_PLAYERS_FLOOR,
    );
  }

  protected canGoFewer(): boolean {
    return this.minLocalPlayers() !== null;
  }

  protected canGoMore(): boolean {
    return (this.minLocalPlayers() ?? 0) < MIN_LOCAL_PLAYERS_CEILING;
  }
}
