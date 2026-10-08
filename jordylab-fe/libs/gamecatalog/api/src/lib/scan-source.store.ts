import { inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, Observable, of } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import {
  HealthExceptions,
  HealthKind,
  HideImpact,
  LibraryHealth,
  LibraryStatus,
  LibrarySyncRun,
  ScanLibraryType,
  ScanSource,
} from './gamecatalog.models';
import { librarySyncFailureMessage } from './library-sync-failure';

@Injectable({ providedIn: 'root' })
export class ScanSourceStore {
  readonly #api = inject(GameCatalogApiService);

  readonly #sources = signal<ScanSource[]>([]);
  readonly #health = signal<LibraryHealth | null>(null);
  readonly #healthExceptions = signal<HealthExceptions | null>(null);
  readonly #loadingExceptions = signal<HealthKind | null>(null);
  readonly #loading = signal(true);
  readonly #error = signal<string | null>(null);
  readonly #togglingId = signal<string | null>(null);
  readonly #disableQuote = signal<{ source: ScanSource; impact: HideImpact } | null>(null);
  readonly #editingSourceId = signal<string | null>(null);
  readonly #renamingHostId = signal<string | null>(null);
  readonly #hostNameProblem = signal<string | null>(null);
  readonly #downloading = signal<ScanLibraryType | null>(null);
  readonly #libraryStatus = signal<LibraryStatus | null>(null);
  readonly #librarySyncing = signal<'OWNED' | 'FAMILY' | null>(null);
  readonly #lastLibraryRun = signal<LibrarySyncRun | null>(null);

  readonly sources = this.#sources.asReadonly();
  readonly health = this.#health.asReadonly();
  /** The games behind one health count while the admin is looking at them. */
  readonly healthExceptions = this.#healthExceptions.asReadonly();
  readonly loadingExceptions = this.#loadingExceptions.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly togglingId = this.#togglingId.asReadonly();
  /** While the "turn this source off?" question is open: the source and how many games would disappear. */
  readonly disableQuote = this.#disableQuote.asReadonly();
  /** The source row whose host name is being edited; one editor is open at a time. */
  readonly editingSourceId = this.#editingSourceId.asReadonly();
  readonly renamingHostId = this.#renamingHostId.asReadonly();
  readonly hostNameProblem = this.#hostNameProblem.asReadonly();
  readonly downloading = this.#downloading.asReadonly();
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

          return of(null);
        })
      )
      .subscribe((overview) => {
        this.#sources.set(overview?.sources ?? []);
        this.#health.set(overview?.health ?? null);
        this.#loading.set(false);
      });
  }

  showHealthExceptions(kind: HealthKind): void {
    this.#loadingExceptions.set(kind);
    this.#api
      .getHealthExceptions(kind)
      .pipe(catchError(() => of(null)))
      .subscribe((exceptions) => {
        this.#loadingExceptions.set(null);
        this.#healthExceptions.set(exceptions);
      });
  }

  hideHealthExceptions(): void {
    this.#healthExceptions.set(null);
  }

  startEditingHostName(sourceId: string): void {
    this.#hostNameProblem.set(null);
    this.#editingSourceId.set(sourceId);
  }

  stopEditingHostName(): void {
    this.#hostNameProblem.set(null);
    this.#editingSourceId.set(null);
  }

  /** A blank name clears it so the hostname shows again; the new name appears on every source of the host. */
  renameHost(hostId: string, displayName: string): void {
    if (this.#renamingHostId()) {
      return;
    }
    this.#renamingHostId.set(hostId);
    this.#hostNameProblem.set(null);

    this.#api
      .setHostDisplayName(hostId, displayName.trim() === '' ? null : displayName.trim())
      .pipe(
        catchError((error: unknown) => {
          this.#hostNameProblem.set(hostNameProblemMessage(error));

          return of(null);
        })
      )
      .subscribe((host) => {
        this.#renamingHostId.set(null);
        if (host) {
          this.#sources.update((list) =>
            list.map((item) =>
              item.hostId === host.id ? { ...item, displayName: host.displayName, label: host.label } : item
            )
          );
          this.#editingSourceId.set(null);
        }
      });
  }

  /**
   * Turning a source on happens at once; turning one off first asks the server what it would hide, so the admin sees the
   * number before anything changes (spec 013 FR-029). If the number cannot be fetched the question still opens, without it.
   */
  requestToggle(source: ScanSource): void {
    if (!source.enabled) {
      this.toggle(source);

      return;
    }
    this.#api
      .getHideImpact(source.id)
      .pipe(catchError(() => of({ hiddenGames: -1, stillVisibleElsewhere: -1 })))
      .subscribe((impact) => this.#disableQuote.set({ source, impact }));
  }

  confirmDisable(): void {
    const quote = this.#disableQuote();
    this.#disableQuote.set(null);
    if (quote) {
      this.toggle(quote.source);
    }
  }

  cancelDisable(): void {
    this.#disableQuote.set(null);
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
        catchError((error: unknown) => {
          this.#error.set(librarySyncFailureMessage(source, error));
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

function hostNameProblemMessage(error: unknown): string {
  const reason = error instanceof HttpErrorResponse ? (error.error as { reason?: string } | null)?.reason : undefined;
  if (reason === 'NAME_TAKEN') {
    return 'That name is already used by another host or console.';
  }
  if (reason === 'NAME_TOO_LONG') {
    return 'A name can be at most 40 characters.';
  }

  return 'Could not save the name. Try again.';
}
