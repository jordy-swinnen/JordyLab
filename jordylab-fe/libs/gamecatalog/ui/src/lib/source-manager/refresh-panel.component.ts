import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RefreshRun, RefreshRunKind } from '@jordylab-fe/gamecatalog/api';
import { ConfirmDialogComponent } from '../dialogs/confirm-dialog.component';

interface RunCard {
  kind: RefreshRunKind;
  title: string;
  explanation: string;
  actionLabel: string;
  run: RefreshRun | null;
}

/**
 * The admin's two bulk refreshes: "Refresh game data" (free: facts and artwork) and "Regenerate AI data" (paid: descriptions).
 * Each shows live progress with Stop, and what happened when it ended. The AI run asks first, in words that name the cost.
 */
@Component({
  selector: 'lib-refresh-panel',
  standalone: true,
  imports: [ConfirmDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './refresh-panel.component.html',
})
export class RefreshPanelComponent {
  dataRun = input<RefreshRun | null>(null);
  aiRun = input<RefreshRun | null>(null);
  starting = input<RefreshRunKind | null>(null);
  aiCostQuote = input<number | null>(null);
  error = input<string | null>(null);

  startData = output<void>();
  requestAi = output<void>();
  confirmAi = output<void>();
  cancelAi = output<void>();
  stopRun = output<RefreshRun>();
  dismissError = output<void>();

  protected readonly cards = computed<RunCard[]>(() => [
    {
      kind: 'DATA',
      title: 'Game data',
      explanation: 'Looks facts and covers up again for every game. Free. Nothing you corrected by hand is changed.',
      actionLabel: 'Refresh game data',
      run: this.dataRun(),
    },
    {
      kind: 'AI',
      title: 'AI descriptions',
      explanation: 'Has the AI write the description again for every game that has no Steam description. Costs money.',
      actionLabel: 'Regenerate AI data',
      run: this.aiRun(),
    },
  ]);

  protected percent(run: RefreshRun): number {
    return run.total === 0 ? 100 : Math.round((run.processed / run.total) * 100);
  }

  protected isRunning(run: RefreshRun | null): boolean {
    return run?.status === 'RUNNING';
  }

  protected outcome(run: RefreshRun): string {
    const done = `${run.processed} of ${run.total} games`;
    switch (run.status) {
      case 'SUCCEEDED':
        return run.failed === 0 ? `Done: ${done} refreshed.` : `Done: ${done}, ${run.failed} could not be refreshed.`;
      case 'STOPPED':
        return `Stopped after ${done}.`;
      case 'INTERRUPTED':
        return `Interrupted by a restart after ${done}. Start it again to continue.`;
      case 'FAILED':
        return run.failureSummary ?? `Failed: no game could be refreshed.`;
      default:
        return `${done} so far.`;
    }
  }

  protected start(card: RunCard): void {
    if (card.kind === 'DATA') {
      this.startData.emit();
    } else {
      this.requestAi.emit();
    }
  }
}
