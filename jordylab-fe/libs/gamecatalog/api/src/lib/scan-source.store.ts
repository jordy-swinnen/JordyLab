import { inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { ScanLibraryType, ScanSource } from './gamecatalog.models';

@Injectable({ providedIn: 'root' })
export class ScanSourceStore {
  readonly #api = inject(GameCatalogApiService);

  readonly #sources = signal<ScanSource[]>([]);
  readonly #loading = signal(true);
  readonly #error = signal<string | null>(null);
  readonly #togglingId = signal<string | null>(null);
  readonly #downloading = signal<ScanLibraryType | null>(null);

  readonly sources = this.#sources.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly togglingId = this.#togglingId.asReadonly();
  readonly downloading = this.#downloading.asReadonly();

  constructor() {
    this.load();
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

  /** Generating the script is state; saving it is a browser side effect, so the blob is handed to `onReady`. */
  downloadScript(libraryType: ScanLibraryType, onReady: (blob: Blob) => void): void {
    if (this.#downloading()) {
      return;
    }

    this.#downloading.set(libraryType);
    this.#error.set(null);

    this.#api
      .getScanScript(libraryType)
      .pipe(
        catchError(() => {
          this.#error.set(`Failed to generate ${libraryType} scan script.`);

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
}
