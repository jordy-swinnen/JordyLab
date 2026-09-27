import { DatePipe } from '@angular/common';
import { Component, input, output, signal } from '@angular/core';
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

  protected readonly tagClass = platformTagClass;
  protected readonly neutralTag =
    'rounded-[7px] border-transparent px-2 py-[5px] font-mono text-[10.5px] font-medium uppercase tracking-[0.08em]';

  onSyncOwned(): void {
    this.syncOwnedLibrary.emit();
  }

  onFamilyTokenInput(event: Event): void {
    this.familyToken.set((event.target as HTMLInputElement).value);
  }

  onSyncFamily(tokenInput: HTMLInputElement): void {
    const token = this.familyToken().trim();
    if (token.length > 0) {
      this.syncFamilyLibrary.emit(token);
      // The token is used only for this one request (FR-011) — clear it from both the signal
      // and the (uncontrolled) input's own value rather than letting it linger in memory/DOM.
      this.familyToken.set('');
      tokenInput.value = '';
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
