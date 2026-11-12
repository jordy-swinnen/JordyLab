import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import {
  GameLibraryStore,
  HealthKind,
  PlayerCountReason,
  RefreshRun,
  RefreshRunStore,
  ScanSource,
  ScanSourceStore,
} from '@jordylab-fe/gamecatalog/api';
import { ExclusionsPanelComponent } from './exclusions-panel.component';
import { RefreshPanelComponent } from './refresh-panel.component';
import { SourceManagerViewComponent } from './source-manager-view.component';

@Component({
  selector: 'lib-source-manager',
  standalone: true,
  imports: [SourceManagerViewComponent, RefreshPanelComponent, ExclusionsPanelComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './source-manager.component.html',
})
export class SourceManagerComponent {
  readonly #store = inject(ScanSourceStore);
  readonly #refreshRuns = inject(RefreshRunStore);
  readonly #library = inject(GameLibraryStore);

  constructor() {
    this.#store.load();
    this.#store.loadExclusions();
  }

  readonly sources = this.#store.sources;
  readonly health = this.#store.health;
  readonly exclusions = this.#store.exclusions;
  readonly exclusionsLoading = this.#store.exclusionsLoading;
  readonly exclusionPending = this.#store.exclusionPending;
  readonly healthExceptions = this.#store.healthExceptions;
  readonly loadingExceptions = this.#store.loadingExceptions;
  readonly loading = this.#store.loading;
  readonly error = this.#store.error;
  readonly togglingId = this.#store.togglingId;
  readonly disableQuote = this.#store.disableQuote;
  readonly editingSourceId = this.#store.editingSourceId;
  readonly renamingHostId = this.#store.renamingHostId;
  readonly hostNameProblem = this.#store.hostNameProblem;
  readonly dataRun = this.#refreshRuns.dataRun;
  readonly aiRun = this.#refreshRuns.aiRun;
  readonly refreshStarting = this.#refreshRuns.starting;
  readonly aiCostQuote = this.#refreshRuns.aiCostQuote;
  readonly refreshError = this.#refreshRuns.error;
  readonly libraryStatus = this.#store.libraryStatus;
  readonly librarySyncing = this.#store.librarySyncing;
  readonly lastLibraryRun = this.#store.lastLibraryRun;

  onToggle(source: ScanSource): void {
    this.#store.requestToggle(source);
  }

  onConfirmDisable(): void {
    this.#store.confirmDisable();
  }

  onCancelDisable(): void {
    this.#store.cancelDisable();
  }

  onEditHostName(sourceId: string): void {
    this.#store.startEditingHostName(sourceId);
  }

  onCancelHostName(): void {
    this.#store.stopEditingHostName();
  }

  onRenameHost(change: { hostId: string; displayName: string }): void {
    this.#store.renameHost(change.hostId, change.displayName);
  }

  onShowExceptions(kind: HealthKind): void {
    this.#store.showHealthExceptions(kind);
  }

  onShowPlayerCountGames(reason: PlayerCountReason): void {
    this.#store.showHealthExceptions('PLAYER_COUNT', reason);
  }

  onIncludeExcluded(gameId: string): void {
    this.#store.includeExcluded(gameId, () => this.#library.reload());
  }

  onHideExceptions(): void {
    this.#store.hideHealthExceptions();
  }

  onStartDataRefresh(): void {
    this.#refreshRuns.startData();
  }

  onRequestAiRefresh(): void {
    this.#refreshRuns.requestAi();
  }

  onConfirmAiRefresh(): void {
    this.#refreshRuns.confirmAi();
  }

  onCancelAiRefresh(): void {
    this.#refreshRuns.cancelAi();
  }

  onStopRefresh(run: RefreshRun): void {
    this.#refreshRuns.stop(run);
  }

  onDismissRefreshError(): void {
    this.#refreshRuns.dismissError();
  }

  onSyncOwnedLibrary(): void {
    this.#store.syncOwnedLibrary();
  }

  onSyncFamilyLibrary(accessToken: string): void {
    this.#store.syncFamilyLibrary(accessToken);
  }

  onReload(): void {
    this.#store.load();
  }

}
