import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { SwitchBulkLine, SwitchBulkSummary, SwitchGameFormat } from './gamecatalog.models';

/** A reviewed line the admin can still change: tick, pick another IGDB match (or none → add by title), format. */
export interface SwitchBulkRow extends SwitchBulkLine {
  /** The chosen IGDB match; null adds the line by its title. */
  igdbGameId: number | null;
  format: SwitchGameFormat;
}

const ERROR_MESSAGE = 'Something went wrong. Please try again.';

/**
 * State for adding many Switch games from a pasted list (009 US3): paste → preview (nothing saved) → review →
 * confirm the ticked lines → summary.
 */
@Injectable({ providedIn: 'root' })
export class SwitchBulkStore {
  readonly #api = inject(GameCatalogApiService);

  readonly #text = signal('');
  readonly #rows = signal<SwitchBulkRow[]>([]);
  readonly #previewing = signal(false);
  readonly #confirming = signal(false);
  readonly #summary = signal<SwitchBulkSummary | null>(null);
  readonly #error = signal<string | null>(null);

  readonly text = this.#text.asReadonly();
  readonly rows = this.#rows.asReadonly();
  readonly previewing = this.#previewing.asReadonly();
  readonly confirming = this.#confirming.asReadonly();
  readonly summary = this.#summary.asReadonly();
  readonly error = this.#error.asReadonly();

  readonly includedCount = computed(() => this.#rows().filter((row) => row.include).length);
  readonly canPreview = computed(() => this.#text().trim().length > 0 && !this.#previewing());
  readonly canConfirm = computed(() => this.includedCount() > 0 && !this.#confirming());

  setText(text: string): void {
    this.#text.set(text);
  }

  preview(): void {
    if (!this.canPreview()) {
      return;
    }
    this.#previewing.set(true);
    this.#error.set(null);
    this.#summary.set(null);
    this.#api
      .previewSwitchBulk(this.#text())
      .pipe(
        catchError((error: { error?: { detail?: string } }) => {
          this.#error.set(error.error?.detail ?? ERROR_MESSAGE);

          return of(null);
        }),
      )
      .subscribe((preview) => {
        this.#previewing.set(false);
        if (preview) {
          this.#rows.set(
            preview.lines.map((line) => ({
              ...line,
              igdbGameId: line.candidates[0]?.igdbGameId ?? null,
              format: 'PHYSICAL',
            })),
          );
        }
      });
  }

  toggleInclude(index: number): void {
    this.#updateRow(index, (row) => ({ ...row, include: !row.include }));
  }

  /** Picks another IGDB match for the line; null adds it by its title instead. */
  chooseMatch(index: number, igdbGameId: number | null): void {
    this.#updateRow(index, (row) => ({ ...row, igdbGameId }));
  }

  setRowFormat(index: number, format: SwitchGameFormat): void {
    this.#updateRow(index, (row) => ({ ...row, format }));
  }

  setAllFormats(format: SwitchGameFormat): void {
    this.#rows.update((rows) => rows.map((row) => ({ ...row, format })));
  }

  confirm(): void {
    if (!this.canConfirm()) {
      return;
    }
    const items = this.#rows()
      .filter((row) => row.include)
      .map((row) => ({
        line: row.line,
        igdbGameId: row.igdbGameId,
        title: row.igdbGameId == null ? row.line : null,
        format: row.format,
      }));
    this.#confirming.set(true);
    this.#error.set(null);
    this.#api
      .confirmSwitchBulk(items)
      .pipe(
        catchError((error: { error?: { detail?: string } }) => {
          this.#error.set(error.error?.detail ?? ERROR_MESSAGE);

          return of(null);
        }),
      )
      .subscribe((summary) => {
        this.#confirming.set(false);
        if (summary) {
          this.#summary.set(summary);
          this.#rows.set([]);
          this.#text.set('');
        }
      });
  }

  reset(): void {
    this.#text.set('');
    this.#rows.set([]);
    this.#previewing.set(false);
    this.#confirming.set(false);
    this.#summary.set(null);
    this.#error.set(null);
  }

  #updateRow(index: number, change: (row: SwitchBulkRow) => SwitchBulkRow): void {
    this.#rows.update((rows) => rows.map((row, i) => (i === index ? change(row) : row)));
  }
}
