import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  catchError,
  debounceTime,
  filter,
  fromEvent,
  of,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { MarkStore } from './mark.store';
import {
  GameSort,
  GameSource,
  GamesPage,
  GameSummary,
  InstallStatus,
  MarkScope,
  MarkType,
  PlaceOption,
  PlatformChip,
  RomStatus,
} from './gamecatalog.models';

export const GAME_LIBRARY_PAGE_SIZE = 60;
const SEARCH_DEBOUNCE_MS = 300;
export const MIN_LOCAL_PLAYERS_FLOOR = 2;
export const MIN_LOCAL_PLAYERS_CEILING = 8;

const INSTALL_STATUSES: readonly InstallStatus[] = [
  'INSTALLED',
  'NOT_INSTALLED',
  'ALL',
];
const SOURCES: readonly GameSource[] = [
  'STEAM_OWNED',
  'STEAM_FAMILY',
  'EMULATED',
  'CONSOLE',
];
const ROM_STATUSES: readonly RomStatus[] = ['UNKNOWN', 'VALIDATED', 'BROKEN'];
const MARKS: readonly MarkType[] = [
  'WANT_TO_PLAY',
  'PLAYED_LIKED',
  'PLAYED_DISLIKED',
];
const SORTS: readonly GameSort[] = ['TITLE', 'MOST_WANTED', 'MOST_LIKED'];

export const INSTALL_STATUS_LABELS: Record<InstallStatus, string> = {
  INSTALLED: 'Installed',
  NOT_INSTALLED: 'Not installed',
  ALL: 'All',
};
export const SOURCE_LABELS: Record<GameSource, string> = {
  STEAM_OWNED: 'Steam (Owned)',
  STEAM_FAMILY: 'Steam (Family)',
  EMULATED: 'Emulated',
  CONSOLE: 'Console',
};
export const ROM_STATUS_LABELS: Record<RomStatus, string> = {
  UNKNOWN: 'Unknown',
  VALIDATED: 'Validated',
  BROKEN: 'Broken ROM',
};
export const MARK_LABELS: Record<MarkType, string> = {
  WANT_TO_PLAY: 'Want to play',
  PLAYED_LIKED: 'Played & liked',
  PLAYED_DISLIKED: 'Played & disliked',
};
export const SORT_LABELS: Record<GameSort, string> = {
  TITLE: 'Title',
  MOST_WANTED: 'Most wanted',
  MOST_LIKED: 'Most liked',
};

/** One filter in use, shown as a removable chip. {@code remove} undoes just this filter. */
export interface ActiveFilter {
  id: string;
  key: string;
  value: string;
  remove: () => void;
}

/** The part of Angular's {@code ParamMap} the store needs, so it never imports the router. */
export interface QueryReader {
  get(name: string): string | null;
  getAll(name: string): string[];
}

interface LibraryFilters {
  search: string;
  platforms: string[];
  where: string[];
  installStatus: InstallStatus;
  sources: GameSource[];
  minLocalPlayers: number | null;
  romStatuses: RomStatus[];
  marks: MarkType[];
  markScope: MarkScope;
  sort: GameSort;
  page: number;
}

const DEFAULT_FILTERS: LibraryFilters = {
  search: '',
  platforms: [],
  where: [],
  installStatus: 'INSTALLED',
  sources: [],
  minLocalPlayers: null,
  romStatuses: [],
  marks: [],
  markScope: 'ALL',
  sort: 'TITLE',
  page: 0,
};

function toggled<T>(values: readonly T[], value: T): T[] {
  return values.includes(value)
    ? values.filter((entry) => entry !== value)
    : [...values, value];
}

function onlyKnown<T extends string>(
  values: string[],
  known: readonly T[],
): T[] {
  return values.filter((value): value is T =>
    (known as readonly string[]).includes(value),
  );
}

@Injectable({ providedIn: 'root' })
export class GameLibraryStore {
  readonly #api = inject(GameCatalogApiService);
  readonly #document = inject(DOCUMENT);
  readonly #marks = inject(MarkStore);
  readonly #queryTrigger = new Subject<void>();
  readonly #searchInput = new Subject<string>();

  readonly #games = signal<GameSummary[]>([]);
  readonly #platforms = signal<PlatformChip[]>([]);
  readonly #places = signal<PlaceOption[]>([]);
  readonly #loading = signal(true);
  readonly #error = signal<string | null>(null);
  readonly #filters = signal<LibraryFilters>(DEFAULT_FILTERS);
  readonly #totalPages = signal(0);
  readonly #totalElements = signal(0);
  readonly #unknownPlayerCount = signal<number | null>(null);

  readonly games = this.#games.asReadonly();
  readonly platforms = this.#platforms.asReadonly();
  readonly places = this.#places.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly totalPages = this.#totalPages.asReadonly();
  readonly totalElements = this.#totalElements.asReadonly();
  /** Games left out of a "N or more players" result because their player count is not known (null when not asked). */
  readonly unknownPlayerCount = this.#unknownPlayerCount.asReadonly();

  readonly searchTerm = computed(() => this.#filters().search);
  readonly selectedPlatforms = computed(() => this.#filters().platforms);
  readonly selectedPlaces = computed(() => this.#filters().where);
  readonly selectedInstallStatus = computed(
    () => this.#filters().installStatus,
  );
  readonly selectedSources = computed(() => this.#filters().sources);
  readonly minLocalPlayers = computed(() => this.#filters().minLocalPlayers);
  readonly selectedRomStatuses = computed(() => this.#filters().romStatuses);
  readonly selectedMarks = computed(() => this.#filters().marks);
  readonly markScope = computed(() => this.#filters().markScope);
  readonly sort = computed(() => this.#filters().sort);
  readonly page = computed(() => this.#filters().page);

  /** Every filter in use as a removable chip, in the order the panel lists them. */
  readonly activeFilters = computed<ActiveFilter[]>(() => {
    const filters = this.#filters();
    const chips: ActiveFilter[] = [];
    const placeLabel = (id: string) =>
      this.#places().find((place) => place.id === id)?.label ?? 'Unknown place';
    const marksKey = filters.markScope === 'MINE' ? 'My marks' : 'Marks';

    filters.platforms.forEach((platform) =>
      chips.push({
        id: `platform:${platform}`,
        key: 'Platform',
        value: platform,
        remove: () => this.togglePlatform(platform),
      }),
    );
    filters.where.forEach((id) =>
      chips.push({
        id: `where:${id}`,
        key: 'Where',
        value: placeLabel(id),
        remove: () => this.togglePlace(id),
      }),
    );
    if (filters.installStatus !== 'ALL') {
      chips.push({
        id: 'status',
        key: 'Status',
        value: INSTALL_STATUS_LABELS[filters.installStatus],
        remove: () => this.selectInstallStatus('ALL'),
      });
    }
    filters.sources.forEach((source) =>
      chips.push({
        id: `source:${source}`,
        key: 'Source',
        value: SOURCE_LABELS[source],
        remove: () => this.toggleSource(source),
      }),
    );
    if (filters.minLocalPlayers) {
      chips.push({
        id: 'players',
        key: 'Players',
        value: `${filters.minLocalPlayers}+`,
        remove: () => this.setMinLocalPlayers(null),
      });
    }
    filters.romStatuses.forEach((status) =>
      chips.push({
        id: `rom:${status}`,
        key: 'ROM',
        value: ROM_STATUS_LABELS[status],
        remove: () => this.toggleRomStatus(status),
      }),
    );
    filters.marks.forEach((mark) =>
      chips.push({
        id: `mark:${mark}`,
        key: marksKey,
        value: MARK_LABELS[mark],
        remove: () => this.toggleMark(mark),
      }),
    );
    if (filters.sort !== 'TITLE') {
      chips.push({
        id: 'sort',
        key: 'Sort',
        value: SORT_LABELS[filters.sort],
        remove: () => this.selectSort('TITLE'),
      });
    }

    return chips;
  });

  /** The filters mirrored into the address bar: empty values and defaults are left out. */
  readonly queryParams = computed<Record<string, string | string[] | null>>(
    () => {
      const filters = this.#filters();

      return {
        q: filters.search || null,
        platform: filters.platforms.length ? filters.platforms : null,
        where: filters.where.length ? filters.where : null,
        status:
          filters.installStatus === 'INSTALLED' ? null : filters.installStatus,
        source: filters.sources.length ? filters.sources : null,
        players: filters.minLocalPlayers
          ? String(filters.minLocalPlayers)
          : null,
        rom: filters.romStatuses.length ? filters.romStatuses : null,
        mark: filters.marks.length ? filters.marks : null,
        scope:
          filters.marks.length && filters.markScope === 'MINE' ? 'MINE' : null,
        sort: filters.sort === 'TITLE' ? null : filters.sort,
        page: filters.page > 0 ? String(filters.page) : null,
      };
    },
  );

  constructor() {
    this.#searchInput
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        filter((term) => term !== this.#filters().search),
        takeUntilDestroyed(),
      )
      .subscribe((term) => {
        this.#change({ search: term });
      });

    this.#queryTrigger
      .pipe(
        startWith(undefined),
        switchMap(() => {
          this.#loading.set(true);
          this.#error.set(null);
          const filters = this.#filters();

          return this.#api
            .getGames({
              search: filters.search || undefined,
              platform: filters.platforms,
              where: filters.where,
              installStatus: filters.installStatus,
              source: filters.sources,
              minLocalPlayers: filters.minLocalPlayers ?? undefined,
              romStatus: filters.romStatuses,
              mark: filters.marks,
              markScope: filters.markScope,
              sort: filters.sort,
              page: filters.page,
              size: GAME_LIBRARY_PAGE_SIZE,
            })
            .pipe(
              catchError(() => {
                this.#error.set('Failed to load games.');

                return of(null);
              }),
            );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((result: GamesPage | null) => {
        this.#loading.set(false);
        if (result) {
          this.#marks.forget();
          this.#games.set(result.content);
          this.#totalPages.set(result.totalPages);
          this.#totalElements.set(result.totalElements);
          this.#unknownPlayerCount.set(result.unknownPlayerCount ?? null);
        }
      });

    // Votes change while the page is in a background tab: look again when the person comes back (FR-044).
    fromEvent(this.#document, 'visibilitychange')
      .pipe(
        filter(() => this.#document.visibilityState === 'visible'),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.#queryTrigger.next());

    this.#api
      .getPlatforms()
      .pipe(catchError(() => of([])))
      .subscribe((platforms) => this.#platforms.set(platforms));

    this.#api
      .getPlaces()
      .pipe(catchError(() => of([])))
      .subscribe((places) => this.#places.set(places));
  }

  /** Restores the filters from the address bar; does nothing (and loads nothing) when they already match. */
  applyQuery(reader: QueryReader): void {
    const players = Number(reader.get('players'));
    const page = Number(reader.get('page'));
    const status = reader.get('status') as InstallStatus | null;
    const sort = reader.get('sort') as GameSort | null;
    const restored: LibraryFilters = {
      search: reader.get('q') ?? '',
      platforms: reader.getAll('platform'),
      where: reader.getAll('where'),
      installStatus:
        status && INSTALL_STATUSES.includes(status) ? status : 'INSTALLED',
      sources: onlyKnown(reader.getAll('source'), SOURCES),
      minLocalPlayers:
        Number.isInteger(players) &&
        players >= MIN_LOCAL_PLAYERS_FLOOR &&
        players <= MIN_LOCAL_PLAYERS_CEILING
          ? players
          : null,
      romStatuses: onlyKnown(reader.getAll('rom'), ROM_STATUSES),
      marks: onlyKnown(reader.getAll('mark'), MARKS),
      markScope: reader.get('scope') === 'MINE' ? 'MINE' : 'ALL',
      sort: sort && SORTS.includes(sort) ? sort : 'TITLE',
      page: Number.isInteger(page) && page > 0 ? page : 0,
    };
    if (JSON.stringify(restored) === JSON.stringify(this.#filters())) {
      return;
    }
    this.#filters.set(restored);
    this.#queryTrigger.next();
  }

  search(term: string): void {
    this.#searchInput.next(term);
  }

  togglePlatform(platform: string): void {
    this.#change({ platforms: toggled(this.#filters().platforms, platform) });
  }

  togglePlace(placeId: string): void {
    this.#change({ where: toggled(this.#filters().where, placeId) });
  }

  selectInstallStatus(status: InstallStatus): void {
    this.#change({ installStatus: status });
  }

  toggleSource(source: GameSource): void {
    this.#change({ sources: toggled(this.#filters().sources, source) });
  }

  /** {@code null} (or anything under two) means "any number of players". */
  setMinLocalPlayers(players: number | null): void {
    const valid = players !== null && players >= MIN_LOCAL_PLAYERS_FLOOR;
    this.#change({
      minLocalPlayers: valid
        ? Math.min(players, MIN_LOCAL_PLAYERS_CEILING)
        : null,
    });
  }

  toggleRomStatus(status: RomStatus): void {
    this.#change({ romStatuses: toggled(this.#filters().romStatuses, status) });
  }

  toggleMark(mark: MarkType): void {
    this.#change({ marks: toggled(this.#filters().marks, mark) });
  }

  selectMarkScope(scope: MarkScope): void {
    this.#change({ markScope: scope });
  }

  selectSort(sort: GameSort): void {
    this.#change({ sort });
  }

  /** Removes every filter, including the default "Installed" status, and the sort; the search box is kept. */
  clearAll(): void {
    this.#change({
      platforms: [],
      where: [],
      installStatus: 'ALL',
      sources: [],
      minLocalPlayers: null,
      romStatuses: [],
      marks: [],
      markScope: 'ALL',
      sort: 'TITLE',
    });
  }

  goToPage(page: number): void {
    this.#filters.update((filters) => ({ ...filters, page }));
    this.#queryTrigger.next();
  }

  #change(changes: Partial<LibraryFilters>): void {
    this.#filters.update((filters) => ({ ...filters, ...changes, page: 0 }));
    this.#queryTrigger.next();
  }
}
