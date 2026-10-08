import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import {
  GameLibraryStore,
  GameSort,
  GameSummary,
  MarkStore,
  GameSource,
  InstallStatus,
  MarkScope,
  MarkType,
  RomStatus,
} from '@jordylab-fe/gamecatalog/api';
import { GameGridViewComponent } from './game-grid-view.component';

@Component({
  selector: 'lib-game-grid',
  standalone: true,
  imports: [GameGridViewComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './game-grid.component.html',
})
export class GameGridComponent {
  readonly #store = inject(GameLibraryStore);
  readonly #router = inject(Router);
  readonly #route = inject(ActivatedRoute);
  readonly #marks = inject(MarkStore);

  /** The loaded games with any mark change of this session on top, so a tap shows at once. */
  readonly games = computed(() => this.#store.games().map((game) => this.#marks.stateOf(game)));
  readonly markPending = this.#marks.pending;
  readonly markError = this.#marks.error;
  readonly platforms = this.#store.platforms;
  readonly places = this.#store.places;
  readonly loading = this.#store.loading;
  readonly error = this.#store.error;
  readonly searchTerm = this.#store.searchTerm;
  readonly selectedPlatforms = this.#store.selectedPlatforms;
  readonly selectedPlaces = this.#store.selectedPlaces;
  readonly selectedInstallStatus = this.#store.selectedInstallStatus;
  readonly selectedSources = this.#store.selectedSources;
  readonly minLocalPlayers = this.#store.minLocalPlayers;
  readonly selectedRomStatuses = this.#store.selectedRomStatuses;
  readonly selectedMarks = this.#store.selectedMarks;
  readonly markScope = this.#store.markScope;
  readonly sort = this.#store.sort;
  readonly activeFilters = this.#store.activeFilters;
  readonly unknownPlayerCount = this.#store.unknownPlayerCount;
  readonly page = this.#store.page;
  readonly totalPages = this.#store.totalPages;
  readonly totalElements = this.#store.totalElements;

  constructor() {
    // The address bar is the source of truth on the way in (reload, Back, a shared link) and a mirror on the way out.
    this.#route.queryParamMap
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((params) => this.#store.applyQuery(params));

    effect(() => {
      const queryParams = this.#store.queryParams();
      void this.#router.navigate([], { relativeTo: this.#route, queryParams, replaceUrl: false });
    });
  }

  onSearch(term: string): void {
    this.#store.search(term);
  }

  onPlatformToggled(platform: string): void {
    this.#store.togglePlatform(platform);
  }

  onPlaceToggled(placeId: string): void {
    this.#store.togglePlace(placeId);
  }

  onInstallStatusSelected(status: InstallStatus): void {
    this.#store.selectInstallStatus(status);
  }

  onSourceToggled(source: GameSource): void {
    this.#store.toggleSource(source);
  }

  onMinLocalPlayersChanged(players: number | null): void {
    this.#store.setMinLocalPlayers(players);
  }

  onRomStatusToggled(status: RomStatus): void {
    this.#store.toggleRomStatus(status);
  }

  onMarkToggled(mark: MarkType): void {
    this.#store.toggleMark(mark);
  }

  onMarkScopeSelected(scope: MarkScope): void {
    this.#store.selectMarkScope(scope);
  }

  onSortSelected(sort: GameSort): void {
    this.#store.selectSort(sort);
  }

  onClearAll(): void {
    this.#store.clearAll();
  }

  onGameMarkChanged(change: { game: GameSummary; mark: MarkType }): void {
    this.#marks.toggle(change.game, change.mark);
  }

  onMarkErrorDismissed(): void {
    this.#marks.dismissError();
  }

  onPageChanged(page: number): void {
    this.#store.goToPage(page);
  }
}
