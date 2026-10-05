import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import {
  coverUrl,
  GAME_LIBRARY_PAGE_SIZE,
  GameSummary,
  InstallStatus,
  LibrarySource,
} from '@jordylab-fe/gamecatalog/api';
import { coverInitials, coverPalette, platformTagClass } from '../cover';

const SKELETON_CARD_COUNT = 10;
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
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './game-grid-view.component.html',
})
export class GameGridViewComponent {
  games = input.required<GameSummary[]>();
  platforms = input.required<string[]>();
  hosts = input.required<string[]>();
  loading = input.required<boolean>();
  error = input.required<string | null>();
  selectedPlatform = input.required<string | null>();
  selectedHost = input.required<string | null>();
  selectedInstallStatus = input.required<InstallStatus>();
  selectedLibrarySource = input.required<LibrarySource | null>();
  localMultiplayerOnly = input.required<boolean>();
  hostFilterAvailable = input.required<boolean>();
  page = input.required<number>();
  totalPages = input.required<number>();
  totalElements = input.required<number>();

  searchChange = output<string>();
  platformChange = output<string | null>();
  hostChange = output<string | null>();
  installStatusChange = output<InstallStatus>();
  librarySourceChange = output<LibrarySource | null>();
  localMultiplayerOnlyChange = output<void>();
  pageChange = output<number>();

  protected readonly skeletonCards = Array.from(
    { length: SKELETON_CARD_COUNT },
    (_, index) => index,
  );
  protected readonly coverUrl = coverUrl;
  protected readonly initials = coverInitials;
  protected readonly palette = coverPalette;
  protected readonly tagClass = platformTagClass;

  protected readonly installStatuses: { value: InstallStatus; label: string }[] = [
    { value: 'INSTALLED', label: 'Installed' },
    { value: 'NOT_INSTALLED', label: 'Not installed' },
    { value: 'ALL', label: 'All' },
  ];
  protected readonly librarySources: { value: LibrarySource; label: string }[] = [
    { value: 'OWNED', label: 'Owned' },
    { value: 'FAMILY', label: 'Family' },
    { value: 'LOCAL', label: 'Local' },
  ];

  protected readonly chipActive = `${CHIP} border-foreground bg-foreground text-background`;
  protected readonly chipIdle = `${CHIP} text-secondary-foreground hover:text-foreground`;

  /** Catalogue number shown on cover plates, continuing across pages. */
  protected catalogNumber(index: number): string {
    return String(this.page() * GAME_LIBRARY_PAGE_SIZE + index + 1).padStart(3, '0');
  }

  protected sourceLabel(source: LibrarySource): string {
    switch (source) {
      case 'OWNED':
        return 'Owned';
      case 'FAMILY':
        return 'Family';
      default:
        return 'Local';
    }
  }

  onSearchInput(event: Event) {
    this.searchChange.emit((event.target as HTMLInputElement).value);
  }

  onPlatformClick(platform: string | null) {
    this.platformChange.emit(platform);
  }

  onHostClick(host: string | null) {
    this.hostChange.emit(host);
  }

  onInstallStatusClick(status: InstallStatus) {
    this.installStatusChange.emit(status);
  }

  onLibrarySourceClick(source: LibrarySource) {
    this.librarySourceChange.emit(
      this.selectedLibrarySource() === source ? null : source,
    );
  }

  onLocalMultiplayerOnlyClick() {
    this.localMultiplayerOnlyChange.emit();
  }

  onPreviousPage() {
    this.pageChange.emit(this.page() - 1);
  }

  onNextPage() {
    this.pageChange.emit(this.page() + 1);
  }
}
