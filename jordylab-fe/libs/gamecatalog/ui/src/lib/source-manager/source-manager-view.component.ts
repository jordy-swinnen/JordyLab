import { DatePipe } from '@angular/common';
import { Component, input, output, signal, ChangeDetectionStrategy } from '@angular/core';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import {
  LibraryStatus,
  LibrarySyncRun,
  ScanSource,
  SourceType,
} from '@jordylab-fe/gamecatalog/api';
import { platformTagClass } from '../cover';
import { readSteamToken } from './steam-token';

export type ScanClientType = 'steam' | 'emudeck';

@Component({
  selector: 'lib-source-manager-view',
  standalone: true,
  imports: [
    DatePipe,
    HlmBadgeDirective,
    HlmButtonDirective,
    HlmInputDirective,
    HlmSkeletonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './source-manager-view.component.html',
})
export class SourceManagerViewComponent {
  sources = input.required<ScanSource[]>();
  loading = input.required<boolean>();
  error = input.required<string | null>();
  togglingId = input.required<string | null>();
  downloading = input.required<ScanClientType | null>();
  refreshingPending = input.required<boolean>();
  refreshProgress = input.required<string | null>();
  libraryStatus = input.required<LibraryStatus | null>();
  librarySyncing = input.required<'OWNED' | 'FAMILY' | null>();
  lastLibraryRun = input.required<LibrarySyncRun | null>();

  toggleSource = output<ScanSource>();
  downloadClient = output<ScanClientType>();
  refreshPending = output<void>();
  syncOwnedLibrary = output<void>();
  syncFamilyLibrary = output<string>();

  protected readonly familyToken = signal('');
  protected readonly tokenProblem = signal<string | null>(null);

  protected readonly tagClass = platformTagClass;
  protected readonly neutralTag =
    'rounded-[7px] border-transparent px-2 py-[5px] font-mono text-[10.5px] font-medium uppercase tracking-[0.08em]';

  onSyncOwned(): void {
    this.syncOwnedLibrary.emit();
  }

  onFamilyTokenInput(event: Event): void {
    this.familyToken.set((event.target as HTMLInputElement).value);
    this.tokenProblem.set(null);
  }

  onSyncFamily(tokenInput: HTMLInputElement): void {
    const pasted = this.familyToken().trim();
    if (pasted.length === 0) {
      this.tokenProblem.set('Paste your Steam token first.');

      return;
    }
    const result = readSteamToken(pasted);
    if ('problem' in result) {
      this.tokenProblem.set(result.problem);

      return;
    }
    this.tokenProblem.set(null);
    this.syncFamilyLibrary.emit(result.token);
    // The token is used only for this one request (FR-011) — clear it from both the signal
    // and the (uncontrolled) input's own value rather than letting it linger in memory/DOM.
    this.familyToken.set('');
    tokenInput.value = '';
  }

  /** What the last sync did, in a sentence: a run that changed nothing must still read as "it worked". */
  runSummary(run: LibrarySyncRun): string {
    const library = run.librarySource === 'FAMILY' ? 'Family' : 'Owned';
    switch (run.outcome) {
      case 'NO_CHANGE':
        return `${library} library is already up to date: Steam reported the same games as last time.`;
      case 'APPLIED':
        return `${library} library synced: ${run.entriesAdded} added, ${run.entriesRemoved} removed.`;
      case 'SUSPICIOUS':
        return `${library} library left unchanged: Steam reported far fewer games than before, which looks like a mistake.`;
      default:
        return `${library} library sync failed.`;
    }
  }

  outcomeClass(outcome: ScanSource['lastOutcome']): string {
    const pill =
      'gap-[7px] rounded-full border-transparent px-[11px] py-1.5 font-mono text-[11px] font-medium uppercase tracking-[0.08em]';
    if (outcome === 'APPLIED' || outcome === 'NO_CHANGE') {
      return `${pill} bg-primary/15 text-primary`;
    }
    if (outcome === 'SCAN_FAILED' || outcome === 'REJECTED') {
      return `${pill} bg-destructive/15 text-destructive`;
    }

    return `${pill} bg-secondary text-muted-foreground`;
  }

  outcomeVariant(
    outcome: ScanSource['lastOutcome'],
  ): 'default' | 'secondary' | 'destructive' | 'outline' {
    if (outcome === 'APPLIED' || outcome === 'NO_CHANGE') {
      return 'default';
    }
    if (outcome === 'SCAN_FAILED' || outcome === 'REJECTED') {
      return 'destructive';
    }

    return 'outline';
  }

  sourceTypeLabel(type: SourceType): string {
    return type === 'STEAM' ? 'Steam library' : 'EmuDeck';
  }
}
