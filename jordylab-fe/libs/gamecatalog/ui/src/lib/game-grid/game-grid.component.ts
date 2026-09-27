import { Component, inject } from '@angular/core';
import { GameLibraryStore } from '@jordylab-fe/gamecatalog/api';
import { GameGridViewComponent } from './game-grid-view.component';

@Component({
  selector: 'lib-game-grid',
  standalone: true,
  imports: [GameGridViewComponent],
  templateUrl: './game-grid.component.html',
})
export class GameGridComponent {
  readonly #store = inject(GameLibraryStore);

  readonly games = this.#store.games;
  readonly platforms = this.#store.platforms;
  readonly hosts = this.#store.hosts;
  readonly loading = this.#store.loading;
  readonly error = this.#store.error;
  readonly selectedPlatform = this.#store.selectedPlatform;
  readonly selectedHost = this.#store.selectedHost;
  readonly page = this.#store.page;
  readonly totalPages = this.#store.totalPages;
  readonly totalElements = this.#store.totalElements;

  onSearch(term: string): void {
    this.#store.search(term);
  }

  onPlatformSelected(platform: string | null): void {
    this.#store.selectPlatform(platform);
  }

  onHostSelected(host: string | null): void {
    this.#store.selectHost(host);
  }

  onPageChanged(page: number): void {
    this.#store.goToPage(page);
  }
}
