import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, distinctUntilChanged, of, startWith, Subject, switchMap } from 'rxjs';
import { GameCatalogApiService, GamesPage, GameSummary } from '@jordylab-fe/gamecatalog/api';
import { GameGridViewComponent } from './game-grid-view.component';

const PAGE_SIZE = 60;
const SEARCH_DEBOUNCE_MS = 300;

@Component({
  selector: 'lib-game-grid',
  standalone: true,
  imports: [GameGridViewComponent],
  templateUrl: './game-grid.component.html',
})
export class GameGridComponent {
  #api = inject(GameCatalogApiService);
  #queryTrigger = new Subject<void>();
  #searchInput = new Subject<string>();

  games = signal<GameSummary[]>([]);
  platforms = signal<string[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  searchTerm = signal('');
  selectedPlatform = signal<string | null>(null);
  page = signal(0);
  totalPages = signal(0);
  totalElements = signal(0);

  constructor() {
    this.#searchInput
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((term) => {
        this.searchTerm.set(term);
        this.page.set(0);
        this.#queryTrigger.next();
      });

    this.#queryTrigger
      .pipe(
        startWith(undefined),
        switchMap(() => {
          this.loading.set(true);
          this.error.set(null);
          return this.#api
            .getGames({
              search: this.searchTerm() || undefined,
              platform: this.selectedPlatform() ?? undefined,
              page: this.page(),
              size: PAGE_SIZE,
            })
            .pipe(
              catchError(() => {
                this.error.set('Failed to load games.');
                return of(null);
              })
            );
        }),
        takeUntilDestroyed()
      )
      .subscribe((result: GamesPage | null) => {
        this.loading.set(false);
        if (result) {
          this.games.set(result.content);
          this.totalPages.set(result.totalPages);
          this.totalElements.set(result.totalElements);
        }
      });

    this.#api
      .getPlatforms()
      .pipe(catchError(() => of([])))
      .subscribe((platforms) => this.platforms.set(platforms));
  }

  onSearch(term: string) {
    this.#searchInput.next(term);
  }

  onPlatformSelected(platform: string | null) {
    this.selectedPlatform.set(platform);
    this.page.set(0);
    this.#queryTrigger.next();
  }

  onPageChanged(page: number) {
    this.page.set(page);
    this.#queryTrigger.next();
  }
}
