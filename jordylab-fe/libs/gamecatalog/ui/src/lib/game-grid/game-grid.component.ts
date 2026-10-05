import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { GameLibraryStore, InstallStatus, LibrarySource } from '@jordylab-fe/gamecatalog/api';
import { GameGridViewComponent } from './game-grid-view.component';

@Component({
  selector: 'lib-game-grid',
  standalone: true,
  imports: [GameGridViewComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
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
  readonly selectedInstallStatus = this.#store.selectedInstallStatus;
  readonly selectedLibrarySource = this.#store.selectedLibrarySource;
  readonly localMultiplayerOnly = this.#store.localMultiplayerOnly;
  readonly hostFilterAvailable = this.#store.hostFilterAvailable;
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

  onInstallStatusSelected(status: InstallStatus): void {
    this.#store.selectInstallStatus(status);
  }

  onLibrarySourceSelected(source: LibrarySource | null): void {
    this.#store.selectLibrarySource(source);
  }

  onLocalMultiplayerOnlyToggled(): void {
    this.#store.toggleLocalMultiplayerOnly();
  }

  onPageChanged(page: number): void {
    this.#store.goToPage(page);
  }
}
