import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ConsoleSearchResult, ConsoleStore, GameConsole } from '@jordylab-fe/gamecatalog/api';
import { ConsolesViewComponent } from './consoles-view.component';

@Component({
  selector: 'lib-consoles',
  standalone: true,
  imports: [ConsolesViewComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './consoles.component.html',
})
export class ConsolesComponent {
  readonly #store = inject(ConsoleStore);

  readonly consoles = this.#store.consoles;
  readonly loading = this.#store.loading;
  readonly error = this.#store.error;
  readonly suggestions = this.#store.suggestions;
  readonly selectedId = this.#store.selectedId;
  readonly games = this.#store.games;
  readonly loadingGames = this.#store.loadingGames;
  readonly results = this.#store.results;
  readonly searching = this.#store.searching;
  readonly noMatchFor = this.#store.noMatchFor;
  readonly busy = this.#store.busy;
  readonly removal = this.#store.removal;
  readonly lastAdded = this.#store.lastAdded;

  constructor() {
    this.#store.load();
    this.#store.suggest('');
  }

  onSuggest(query: string): void {
    this.#store.suggest(query);
  }

  onAddConsole(request: { platform: string; name: string }): void {
    this.#store.addConsole(request.platform, request.name);
  }

  onRename(request: { id: string; name: string }): void {
    this.#store.renameConsole(request.id, request.name);
  }

  onRequestRemoval(console: GameConsole): void {
    this.#store.requestRemoval(console);
  }

  onConfirmRemoval(): void {
    this.#store.confirmRemoval();
  }

  onCancelRemoval(): void {
    this.#store.cancelRemoval();
  }

  onSelectConsole(id: string): void {
    this.#store.selectConsole(id);
  }

  onSearch(query: string): void {
    this.#store.search(query);
  }

  onAddFromSearch(result: ConsoleSearchResult): void {
    this.#store.addGame({ igdbGameId: result.igdbGameId });
  }

  onAddByTitle(title: string): void {
    this.#store.addGame({ title });
  }

  onRelink(request: { gameId: string; igdbGameId: number }): void {
    this.#store.relinkGame(request.gameId, request.igdbGameId);
  }

  onRemoveGame(gameId: string): void {
    this.#store.removeGame(gameId);
  }
}
