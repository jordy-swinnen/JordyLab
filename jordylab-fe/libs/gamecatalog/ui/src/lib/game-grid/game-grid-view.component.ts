import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmCardDirective } from '@spartan-ng/ui-card-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { artworkUrl, GameSummary } from '@jordylab-fe/gamecatalog/api';

const SKELETON_CARD_COUNT = 12;

@Component({
  selector: 'lib-game-grid-view',
  standalone: true,
  imports: [RouterLink, HlmBadgeDirective, HlmCardDirective, HlmInputDirective, HlmSkeletonComponent],
  templateUrl: './game-grid-view.component.html',
})
export class GameGridViewComponent {
  games = input.required<GameSummary[]>();
  platforms = input.required<string[]>();
  loading = input.required<boolean>();
  error = input.required<string | null>();
  selectedPlatform = input.required<string | null>();
  page = input.required<number>();
  totalPages = input.required<number>();
  totalElements = input.required<number>();

  searchChange = output<string>();
  platformChange = output<string | null>();
  pageChange = output<number>();

  protected readonly skeletonCards = Array.from({ length: SKELETON_CARD_COUNT }, (_, index) => index);
  protected readonly artworkUrl = artworkUrl;

  onSearchInput(event: Event) {
    this.searchChange.emit((event.target as HTMLInputElement).value);
  }

  onPlatformClick(platform: string | null) {
    this.platformChange.emit(platform);
  }

  onPreviousPage() {
    this.pageChange.emit(this.page() - 1);
  }

  onNextPage() {
    this.pageChange.emit(this.page() + 1);
  }
}
