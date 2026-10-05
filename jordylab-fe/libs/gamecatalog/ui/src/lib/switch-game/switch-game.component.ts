import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmCardImports } from '@spartan-ng/ui-card-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { SwitchGameFormat, SwitchGameStore, SwitchSearchResult } from '@jordylab-fe/gamecatalog/api';

@Component({
  selector: 'lib-switch-game',
  standalone: true,
  imports: [FormsModule, RouterLink, HlmBadgeDirective, HlmCardImports, HlmInputDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './switch-game.component.html',
})
export class SwitchGameComponent {
  readonly #store = inject(SwitchGameStore);

  readonly results = this.#store.results;
  readonly selectedResult = this.#store.selectedResult;
  readonly manualTitle = this.#store.manualTitle;
  readonly format = this.#store.format;
  readonly mode = this.#store.mode;
  readonly loading = this.#store.loading;
  readonly error = this.#store.error;
  readonly adding = this.#store.adding;
  readonly addError = this.#store.addError;
  readonly added = this.#store.added;
  readonly canAdd = this.#store.canAdd;
  readonly noMatchFor = this.#store.noMatchFor;

  readonly formats: SwitchGameFormat[] = ['PHYSICAL', 'DIGITAL'];

  onSearch(query: string): void {
    this.#store.search(query);
  }

  onSelect(result: SwitchSearchResult): void {
    this.#store.selectResult(result);
  }

  onManualTitle(title: string): void {
    this.#store.setManualTitle(title);
  }

  onFormat(format: SwitchGameFormat): void {
    this.#store.setFormat(format);
  }

  onMode(mode: 'search' | 'manual'): void {
    this.#store.setMode(mode);
  }

  onAddManually(): void {
    this.#store.addNoMatchManually();
  }

  onAdd(): void {
    this.#store.addGame();
  }

  onReset(): void {
    this.#store.reset();
  }
}
