import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { ScanSource, SourceType } from '@jordylab-fe/gamecatalog/api';
import { platformTagClass } from '../cover';

export type ScanClientType = 'steam' | 'emudeck';

@Component({
  selector: 'lib-source-manager-view',
  standalone: true,
  imports: [
    DatePipe,
    HlmBadgeDirective,
    HlmButtonDirective,
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

  toggleSource = output<ScanSource>();
  downloadClient = output<ScanClientType>();
  refreshPending = output<void>();

  protected readonly tagClass = platformTagClass;
  protected readonly neutralTag =
    'rounded-[7px] border-transparent px-2 py-[5px] font-mono text-[10.5px] font-medium uppercase tracking-[0.08em]';

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
