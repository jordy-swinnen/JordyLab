import { Component, inject } from '@angular/core';
import { ScanSource, ScanSourceStore } from '@jordylab-fe/gamecatalog/api';
import { ScanClientType, SourceManagerViewComponent } from './source-manager-view.component';

@Component({
  selector: 'lib-source-manager',
  standalone: true,
  imports: [SourceManagerViewComponent],
  templateUrl: './source-manager.component.html',
})
export class SourceManagerComponent {
  readonly #store = inject(ScanSourceStore);

  readonly sources = this.#store.sources;
  readonly loading = this.#store.loading;
  readonly error = this.#store.error;
  readonly togglingId = this.#store.togglingId;
  readonly downloading = this.#store.downloading;
  readonly refreshingPending = this.#store.refreshingPending;
  readonly refreshProgress = this.#store.refreshProgress;
  readonly libraryStatus = this.#store.libraryStatus;
  readonly librarySyncing = this.#store.librarySyncing;
  readonly lastLibraryRun = this.#store.lastLibraryRun;

  onToggle(source: ScanSource): void {
    this.#store.toggle(source);
  }

  onRefreshPending(): void {
    this.#store.refreshPending();
  }

  onSyncOwnedLibrary(): void {
    this.#store.syncOwnedLibrary();
  }

  onSyncFamilyLibrary(accessToken: string): void {
    this.#store.syncFamilyLibrary(accessToken);
  }

  onDownloadClient(libraryType: ScanClientType): void {
    this.#store.downloadClient(libraryType, (blob) =>
      triggerBrowserDownload(blob, `jordylab-scan-${libraryType}.py`)
    );
  }
}

function triggerBrowserDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
