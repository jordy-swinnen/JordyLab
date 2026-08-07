import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmCardDirective } from '@spartan-ng/ui-card-helm';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { ScanSource, SourceType } from '@jordylab-fe/gamecatalog/api';

export type ScanScriptType = 'steam' | 'emudeck';

@Component({
  selector: 'lib-source-manager-view',
  standalone: true,
  imports: [DatePipe, HlmBadgeDirective, HlmCardDirective, HlmButtonDirective, HlmSkeletonComponent],
  templateUrl: './source-manager-view.component.html',
})
export class SourceManagerViewComponent {
  sources = input.required<ScanSource[]>();
  loading = input.required<boolean>();
  error = input.required<string | null>();
  togglingId = input.required<string | null>();
  downloading = input.required<ScanScriptType | null>();

  toggleSource = output<ScanSource>();
  downloadScript = output<ScanScriptType>();

  outcomeVariant(outcome: ScanSource['lastOutcome']): 'default' | 'secondary' | 'destructive' | 'outline' {
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
