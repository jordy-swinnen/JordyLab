import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { bannerUrl, coverUrl, GameDetail } from '@jordylab-fe/gamecatalog/api';
import { coverInitials, coverPalette, platformTagClass } from '../cover';

@Component({
  selector: 'lib-game-detail-view',
  standalone: true,
  imports: [RouterLink, HlmBadgeDirective, HlmSkeletonComponent],
  templateUrl: './game-detail-view.component.html',
})
export class GameDetailViewComponent {
  game = input.required<GameDetail | null>();
  loading = input.required<boolean>();
  notFound = input.required<boolean>();
  error = input.required<string | null>();
  refreshingMetadata = input.required<boolean>();
  refreshingEnrichment = input.required<boolean>();

  refreshMetadata = output<void>();
  refreshEnrichment = output<void>();

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
}
