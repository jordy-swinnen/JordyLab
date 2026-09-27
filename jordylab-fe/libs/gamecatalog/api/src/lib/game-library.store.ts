import { computed, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, distinctUntilChanged, of, startWith, Subject, switchMap } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { GamesPage, GameSummary, InstallStatus, LibrarySource } from './gamecatalog.models';

export const GAME_LIBRARY_PAGE_SIZE = 60;
const SEARCH_DEBOUNCE_MS = 300;

@Injectable({ providedIn: 'root' })
export class GameLibraryStore {
  readonly #api = inject(GameCatalogApiService);
  readonly #queryTrigger = new Subject<void>();
  readonly #searchInput = new Subject<string>();

  readonly #games = signal<GameSummary[]>([]);
  readonly #platforms = signal<string[]>([]);
  readonly #hosts = signal<string[]>([]);
  readonly #loading = signal(true);
  readonly #error = signal<string | null>(null);
  readonly #searchTerm = signal('');
  readonly #selectedPlatform = signal<string | null>(null);
  readonly #selectedHost = signal<string | null>(null);
  readonly #selectedInstallStatus = signal<InstallStatus>('INSTALLED');
  readonly #selectedLibrarySource = signal<LibrarySource | null>(null);
  readonly #localMultiplayerOnly = signal(false);
  readonly #page = signal(0);
  readonly #totalPages = signal(0);
  readonly #totalElements = signal(0);

  readonly games = this.#games.asReadonly();
  readonly platforms = this.#platforms.asReadonly();
  readonly hosts = this.#hosts.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly searchTerm = this.#searchTerm.asReadonly();
  readonly selectedPlatform = this.#selectedPlatform.asReadonly();
  readonly selectedHost = this.#selectedHost.asReadonly();
  readonly selectedInstallStatus = this.#selectedInstallStatus.asReadonly();
  readonly selectedLibrarySource = this.#selectedLibrarySource.asReadonly();
  readonly localMultiplayerOnly = this.#localMultiplayerOnly.asReadonly();
  readonly page = this.#page.asReadonly();
  readonly totalPages = this.#totalPages.asReadonly();
  readonly totalElements = this.#totalElements.asReadonly();

  /** The host filter is not offered while "Not installed" is selected (FR-023). */
  readonly hostFilterAvailable = computed(() => this.#selectedInstallStatus() !== 'NOT_INSTALLED');

  constructor() {
    this.#searchInput
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((term) => {
        this.#searchTerm.set(term);
        this.#page.set(0);
        this.#queryTrigger.next();
      });

    this.#queryTrigger
      .pipe(
        startWith(undefined),
        switchMap(() => {
          this.#loading.set(true);
          this.#error.set(null);
          const selectedLibrarySource = this.#selectedLibrarySource();

          return this.#api
            .getGames({
              search: this.#searchTerm() || undefined,
              platform: this.#selectedPlatform() ?? undefined,
              host: this.#selectedHost() ?? undefined,
              installStatus: this.#selectedInstallStatus(),
              librarySource: selectedLibrarySource ? [selectedLibrarySource] : undefined,
              localMultiplayer: this.#localMultiplayerOnly() || undefined,
              page: this.#page(),
              size: GAME_LIBRARY_PAGE_SIZE,
            })
            .pipe(
              catchError(() => {
                this.#error.set('Failed to load games.');

                return of(null);
              })
            );
        }),
        takeUntilDestroyed()
      )
      .subscribe((result: GamesPage | null) => {
        this.#loading.set(false);
        if (result) {
          this.#games.set(result.content);
          this.#totalPages.set(result.totalPages);
          this.#totalElements.set(result.totalElements);
        }
      });

    this.#api
      .getPlatforms()
      .pipe(catchError(() => of([])))
      .subscribe((platforms) => this.#platforms.set(platforms));

    this.#api
      .getHosts()
      .pipe(catchError(() => of([])))
      .subscribe((hosts) => this.#hosts.set(hosts));
  }

  search(term: string): void {
    this.#searchInput.next(term);
  }

  selectPlatform(platform: string | null): void {
    this.#selectedPlatform.set(platform);
    this.#page.set(0);
    this.#queryTrigger.next();
  }

  selectHost(host: string | null): void {
    this.#selectedHost.set(host);
    this.#page.set(0);
    this.#queryTrigger.next();
  }

  selectInstallStatus(status: InstallStatus): void {
    this.#selectedInstallStatus.set(status);
    if (status === 'NOT_INSTALLED') {
      this.#selectedHost.set(null);
    }
    this.#page.set(0);
    this.#queryTrigger.next();
  }

  selectLibrarySource(source: LibrarySource | null): void {
    this.#selectedLibrarySource.set(source);
    this.#page.set(0);
    this.#queryTrigger.next();
  }

  toggleLocalMultiplayerOnly(): void {
    this.#localMultiplayerOnly.update((value) => !value);
    this.#page.set(0);
    this.#queryTrigger.next();
  }

  goToPage(page: number): void {
    this.#page.set(page);
    this.#queryTrigger.next();
  }
}
