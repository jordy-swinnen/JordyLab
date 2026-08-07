import { Component, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { GameCatalogApiService, ScanSource } from '@jordylab-fe/gamecatalog/api';
import { ScanScriptType, SourceManagerViewComponent } from './source-manager-view.component';

@Component({
  selector: 'lib-source-manager',
  standalone: true,
  imports: [SourceManagerViewComponent],
  templateUrl: './source-manager.component.html',
})
export class SourceManagerComponent {
  #api = inject(GameCatalogApiService);

  sources = signal<ScanSource[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  togglingId = signal<string | null>(null);
  downloading = signal<ScanScriptType | null>(null);

  constructor() {
    this.#api
      .getSources()
      .pipe(
        catchError(() => {
          this.error.set('Failed to load scan sources.');
          this.loading.set(false);
          return of([]);
        })
      )
      .subscribe((sources) => {
        this.sources.set(sources);
        this.loading.set(false);
      });
  }

  onToggle(source: ScanSource) {
    if (this.togglingId()) {
      return;
    }

    this.togglingId.set(source.id);
    this.error.set(null);
    this.#api
      .setSourceEnabled(source.id, !source.enabled)
      .pipe(
        catchError(() => {
          this.error.set(`Failed to update '${source.sourceKey}'.`);
          return of(null);
        })
      )
      .subscribe((response) => {
        this.togglingId.set(null);
        if (response) {
          this.sources.update((list) =>
            list.map((item) => (item.id === response.id ? { ...item, enabled: response.enabled } : item))
          );
        }
      });
  }

  onDownloadScript(libraryType: ScanScriptType) {
    if (this.downloading()) {
      return;
    }
    this.downloading.set(libraryType);
    this.error.set(null);
    this.#api
      .getScanScript(libraryType)
      .pipe(
        catchError(() => {
          this.error.set(`Failed to generate ${libraryType} scan script.`);
          this.downloading.set(null);
          return of(null);
        })
      )
      .subscribe((blob) => {
        this.downloading.set(null);
        if (blob) {
          triggerBrowserDownload(blob, `jordylab-scan-${libraryType}.sh`);
        }
      });
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
