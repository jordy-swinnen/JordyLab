import { inject, Injectable, signal } from '@angular/core';
import { catchError, Observable, of } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { LibraryStatus, LibrarySyncRun, ScanLibraryType, ScanSource } from './gamecatalog.models';

@Injectable({ providedIn: 'root' })
export class ScanSourceStore {
  readonly #api = inject(GameCatalogApiService);

  readonly #sources = signal<ScanSource[]>([]);
  readonly #loading = signal(true);
  readonly #error = signal<string | null>(null);
  readonly #togglingId = signal<string | null>(null);
  readonly #downloading = signal<ScanLibraryType | null>(null);
  readonly #refreshingPending = signal(false);
  readonly #refreshProgress = signal<string | null>(null);
  readonly #libraryStatus = signal<LibraryStatus | null>(null);
  readonly #librarySyncing = signal<'OWNED' | 'FAMILY' | null>(null);
  readonly #lastLibraryRun = signal<LibrarySyncRun | null>(null);

  readonly sources = this.#sources.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly togglingId = this.#togglingId.asReadonly();
  readonly downloading = this.#downloading.asReadonly();
  readonly refreshingPending = this.#refreshingPending.asReadonly();
  readonly refreshProgress = this.#refreshProgress.asReadonly();
  readonly libraryStatus = this.#libraryStatus.asReadonly();
  readonly librarySyncing = this.#librarySyncing.asReadonly();
  readonly lastLibraryRun = this.#lastLibraryRun.asReadonly();

  constructor() {
    this.load();
    this.loadLibraryStatus();
  }

  load(): void {
    this.#loading.set(true);
    this.#error.set(null);

    this.#api
      .getSources()
      .pipe(
        catchError(() => {
          this.#error.set('Failed to load scan sources.');

          return of([]);
        })
      )
      .subscribe((sources) => {
        this.#sources.set(sources);
        this.#loading.set(false);
      });
  }

  toggle(source: ScanSource): void {
    if (this.#togglingId()) {
      return;
    }

    this.#togglingId.set(source.id);
    this.#error.set(null);

    this.#api
      .setSourceEnabled(source.id, !source.enabled)
      .pipe(
        catchError(() => {
          this.#error.set(`Failed to update '${source.sourceKey}'.`);

          return of(null);
        })
      )
      .subscribe((response) => {
        this.#togglingId.set(null);
        if (response) {
          this.#sources.update((list) =>
            list.map((item) => (item.id === response.id ? { ...item, enabled: response.enabled } : item))
          );
        }
      });
  }

  /**
   * Drains leftover PENDING/FAILED deterministic + AI data in bounded batches, repeating until
   * nothing remains. Stops (with an explicit error) if a batch makes no progress, so an unavailable
   * AI provider cannot spin the loop.
   */
  refreshPending(): void {
    if (this.#refreshingPending()) {
      return;
    }

    this.#refreshingPending.set(true);
    this.#refreshProgress.set('Refreshing catalog data…');
    this.#error.set(null);
    this.#drainPending(Number.POSITIVE_INFINITY);
  }

  #drainPending(previousRemaining: number): void {
    this.#api
      .refreshPending()
      .pipe(
        catchError(() => {
          this.#error.set('Failed to refresh catalog data.');
          this.#refreshingPending.set(false);
          this.#refreshProgress.set(null);

          return of(null);
        })
      )
      .subscribe((result) => {
        if (!result) {
          return;
        }

        const remaining =
          result.metadata.remaining + result.enrichment.remaining + result.multiplayer.remaining;
        if (remaining === 0) {
          this.#refreshingPending.set(false);
          this.#refreshProgress.set(null);

          return;
        }
        if (remaining >= previousRemaining) {
          this.#error.set(`Refresh stalled with ${remaining} game(s) still pending.`);
          this.#refreshingPending.set(false);
          this.#refreshProgress.set(null);

          return;
        }

        this.#refreshProgress.set(`Refreshing… ${remaining} left`);
        this.#drainPending(remaining);
      });
  }
  /** Generating the client is state; saving it is a browser side effect, so the blob is handed to `onReady`. */
  downloadClient(libraryType: ScanLibraryType, onReady: (blob: Blob) => void): void {
    if (this.#downloading()) {
      return;
    }
    this.#downloading.set(libraryType);
    this.#error.set(null);

    this.#api
      .getScanClient(libraryType)
      .pipe(
        catchError(() => {
          this.#error.set(`Failed to generate the ${libraryType} scan client.`);

          return of(null);
        })
      )
      .subscribe((blob) => {
        this.#downloading.set(null);
        if (blob) {
          onReady(blob);
        }
      });
  }

  loadLibraryStatus(): void {
    this.#api
      .getLibraryStatus()
      .pipe(catchError(() => of(null)))
      .subscribe((status) => this.#libraryStatus.set(status));
  }

  syncOwnedLibrary(): void {
    this.#runLibrarySync('OWNED', () => this.#api.syncOwnedLibrary());
  }

  syncFamilyLibrary(accessToken: string): void {
    this.#runLibrarySync('FAMILY', () => this.#api.syncFamilyLibrary(accessToken));
  }

  #runLibrarySync(source: 'OWNED' | 'FAMILY', call: () => Observable<LibrarySyncRun>): void {
    if (this.#librarySyncing()) {
      return;
    }

    this.#librarySyncing.set(source);
    this.#error.set(null);

    call()
      .pipe(
        catchError(() => {
          this.#error.set(`Failed to sync the ${source.toLowerCase()} library.`);
          this.#librarySyncing.set(null);

          return of(null);
        })
      )
      .subscribe((run) => {
        this.#librarySyncing.set(null);
        if (run) {
          this.#lastLibraryRun.set(run);
          this.loadLibraryStatus();
        }
      });
  }
}
