import { Component, input, output, signal, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import {
  bannerUrl,
  coverUrl,
  GameDetail,
  SwitchGameFormat,
  SwitchSearchResult,
} from '@jordylab-fe/gamecatalog/api';
import { coverInitials, coverPalette, platformTagClass } from '../cover';

@Component({
  selector: 'lib-game-detail-view',
  standalone: true,
  imports: [RouterLink, HlmBadgeDirective, HlmSkeletonComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './game-detail-view.component.html',
})
export class GameDetailViewComponent {
  game = input.required<GameDetail | null>();
  loading = input.required<boolean>();
  notFound = input.required<boolean>();
  error = input.required<string | null>();
  refreshingMetadata = input.required<boolean>();
  refreshingEnrichment = input.required<boolean>();
  canAdminister = input.required<boolean>();
  savingSwitch = input.required<boolean>();
  relinkCandidates = input.required<SwitchSearchResult[]>();

  refreshMetadata = output<void>();
  refreshEnrichment = output<void>();
  changeSwitchFormat = output<SwitchGameFormat>();
  searchRelink = output<string>();
  relink = output<number>();
  removeSwitch = output<void>();

  protected readonly switchFormats: SwitchGameFormat[] = ['PHYSICAL', 'DIGITAL'];
  protected readonly confirmingRemove = signal(false);

  protected readonly coverUrl = coverUrl;
  protected readonly bannerUrl = bannerUrl;
  protected readonly initials = coverInitials;
  protected readonly palette = coverPalette;
  protected readonly tagClass = platformTagClass;

  protected hostnames(game: GameDetail): string {
    return game.hosts.map((host) => host.hostname).join(', ');
  }

  protected sourceLabel(game: GameDetail): string {
    switch (game.librarySource) {
      case 'OWNED':
        return 'Owned';
      case 'FAMILY':
        return 'Family';
      default:
        return 'Local';
    }
  }

  /** The Switch copy's format, or null for scanned games (hostFormats only lists manual hosts). */
  protected switchFormat(game: GameDetail): SwitchGameFormat | null {
    return game.hostFormats['Nintendo Switch'] ?? null;
  }

  protected formatLabel(format: SwitchGameFormat): string {
    return format === 'PHYSICAL' ? 'Physical' : 'Digital';
  }

  protected onFormatChange(event: Event): void {
    this.changeSwitchFormat.emit((event.target as HTMLSelectElement).value as SwitchGameFormat);
  }

  protected onRelinkSearch(event: Event): void {
    this.searchRelink.emit((event.target as HTMLInputElement).value);
  }

  protected onConfirmRemove(): void {
    this.confirmingRemove.set(false);
    this.removeSwitch.emit();
  }
}
