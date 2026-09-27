import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { artworkUrl, GameDetail } from '@jordylab-fe/gamecatalog/api';
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

  protected readonly artworkUrl = artworkUrl;
  protected readonly initials = coverInitials;
  protected readonly palette = coverPalette;
  protected readonly tagClass = platformTagClass;
}
