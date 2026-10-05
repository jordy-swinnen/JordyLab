import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { SwitchBulkRow, SwitchBulkStatus, SwitchBulkStore, SwitchGameFormat } from '@jordylab-fe/gamecatalog/api';

const STATUS_LABELS: Record<SwitchBulkStatus, string> = {
  MATCH: 'Match',
  NEEDS_REVIEW: 'Check',
  NO_MATCH: 'No match',
  ALREADY_PRESENT: 'Already in catalog',
};

/** Adds many Switch games from a pasted list: paste, review the matches, confirm (009 US3). */
@Component({
  selector: 'lib-switch-bulk',
  standalone: true,
  imports: [FormsModule, RouterLink, HlmBadgeDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './switch-bulk.component.html',
})
export class SwitchBulkComponent {
  readonly #store = inject(SwitchBulkStore);

  readonly text = this.#store.text;
  readonly rows = this.#store.rows;
  readonly previewing = this.#store.previewing;
  readonly confirming = this.#store.confirming;
  readonly summary = this.#store.summary;
  readonly error = this.#store.error;
  readonly includedCount = this.#store.includedCount;
  readonly canPreview = this.#store.canPreview;
  readonly canConfirm = this.#store.canConfirm;

  readonly formats: SwitchGameFormat[] = ['PHYSICAL', 'DIGITAL'];

  statusLabel(status: SwitchBulkStatus): string {
    return STATUS_LABELS[status];
  }

  coverOf(row: SwitchBulkRow): string | null {
    return row.candidates.find((candidate) => candidate.igdbGameId === row.igdbGameId)?.coverUrl ?? null;
  }

  onText(text: string): void {
    this.#store.setText(text);
  }

  onPreview(): void {
    this.#store.preview();
  }

  onToggle(index: number): void {
    this.#store.toggleInclude(index);
  }

  /** The select's value is an IGDB id, or '' for "add by title". */
  onMatch(index: number, value: string): void {
    this.#store.chooseMatch(index, value === '' ? null : Number(value));
  }

  onRowFormat(index: number, format: SwitchGameFormat): void {
    this.#store.setRowFormat(index, format);
  }

  onAllFormats(format: SwitchGameFormat): void {
    this.#store.setAllFormats(format);
  }

  onConfirm(): void {
    this.#store.confirm();
  }

  onReset(): void {
    this.#store.reset();
  }
}
