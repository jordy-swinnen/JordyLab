import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { artworkUrl, GameSummary } from '@jordylab-fe/gamecatalog/api';
import { coverInitials, coverPalette, platformTagClass } from '../cover';

const SKELETON_CARD_COUNT = 10;
const PAGE_SIZE = 60;
const CHIP = 'h-10 cursor-pointer px-4 text-sm font-semibold';

@Component({
  selector: 'lib-game-grid-view',
  standalone: true,
  imports: [
    RouterLink,
    HlmBadgeDirective,
    HlmInputDirective,
    HlmSkeletonComponent,
  ],
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

  protected readonly skeletonCards = Array.from(
    { length: SKELETON_CARD_COUNT },
    (_, index) => index,
  );
  protected readonly artworkUrl = artworkUrl;
  protected readonly initials = coverInitials;
  protected readonly palette = coverPalette;
  protected readonly tagClass = platformTagClass;

  protected readonly chipActive = `${CHIP} border-foreground bg-foreground text-background`;
  protected readonly chipIdle = `${CHIP} text-secondary-foreground hover:text-foreground`;

  /** Catalogue number shown on cover plates, continuing across pages. */
  protected catalogNumber(index: number): string {
    return String(this.page() * PAGE_SIZE + index + 1).padStart(3, '0');
  }

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
