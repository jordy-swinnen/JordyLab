import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import {
  ActiveFilter,
  coverUrl,
  GAME_LIBRARY_PAGE_SIZE,
  GameSort,
  GameSource,
  GameSummary,
  InstallStatus,
  MarkScope,
  MarkType,
  PlaceOption,
  PlatformChip,
  RomStatus,
  RomSummary,
} from '@jordylab-fe/gamecatalog/api';
import { PlatformChipComponent } from '../chips/platform-chip.component';
import { MarkButtonsComponent } from '../marks/mark-buttons.component';
import { VoteRailComponent } from '../marks/vote-rail.component';
import { RomChipComponent } from '../chips/rom-chip.component';
import { SourceLabelComponent } from '../chips/source-label.component';
import { StatusChipComponent } from '../chips/status-chip.component';
import { coverInitials, coverPalette } from '../cover';
import { ActiveFiltersComponent } from './active-filters.component';
import { FilterBarComponent } from './filter-bar.component';
import { FiltersPanelComponent } from './filters-panel.component';

const SKELETON_CARD_COUNT = 10;

@Component({
  selector: 'lib-game-grid-view',
  standalone: true,
  imports: [
    RouterLink,
    HlmBadgeDirective,
    HlmSkeletonComponent,
    PlatformChipComponent,
    RomChipComponent,
    MarkButtonsComponent,
    VoteRailComponent,
    SourceLabelComponent,
    StatusChipComponent,
    FilterBarComponent,
    FiltersPanelComponent,
    ActiveFiltersComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './game-grid-view.component.html',
})
export class GameGridViewComponent {
  protected readonly filterBar = viewChild.required(FilterBarComponent);

  games = input.required<GameSummary[]>();
  platforms = input.required<PlatformChip[]>();
  places = input.required<PlaceOption[]>();
  loading = input.required<boolean>();
  error = input.required<string | null>();
  searchTerm = input.required<string>();
  selectedPlatforms = input.required<string[]>();
  selectedPlaces = input.required<string[]>();
  selectedInstallStatus = input.required<InstallStatus>();
  selectedSources = input.required<GameSource[]>();
  minLocalPlayers = input.required<number | null>();
  selectedRomStatuses = input.required<RomStatus[]>();
  selectedMarks = input.required<MarkType[]>();
  markScope = input.required<MarkScope>();
  sort = input.required<GameSort>();
  activeFilters = input.required<ActiveFilter[]>();
  unknownPlayerCount = input<number | null>(null);
  page = input.required<number>();
  totalPages = input.required<number>();
  totalElements = input.required<number>();
  markPending = input<ReadonlySet<string>>(new Set());
  markError = input<string | null>(null);

  searchChange = output<string>();
  platformToggle = output<string>();
  placeToggle = output<string>();
  installStatusChange = output<InstallStatus>();
  sourceToggle = output<GameSource>();
  minLocalPlayersChange = output<number | null>();
  romStatusToggle = output<RomStatus>();
  markToggle = output<MarkType>();
  markScopeChange = output<MarkScope>();
  sortChange = output<GameSort>();
  clearAll = output<void>();
  pageChange = output<number>();
  gameMarkChange = output<{ game: GameSummary; mark: MarkType }>();
  markErrorDismiss = output<void>();

  protected readonly panelOpen = signal(false);

  protected readonly skeletonCards = Array.from(
    { length: SKELETON_CARD_COUNT },
    (_, index) => index,
  );
  protected readonly coverUrl = coverUrl;
  protected readonly initials = coverInitials;
  protected readonly palette = coverPalette;

  /** The filter to offer for removal when nothing matches: the status first, since it is the one most often forgotten. */
  protected readonly filterToRelax = computed(
    () => this.activeFilters().find((filter) => filter.id === 'status') ?? this.activeFilters()[0] ?? null,
  );

  /** Catalogue number shown on cover plates, continuing across pages. */
  protected catalogNumber(index: number): string {
    return String(this.page() * GAME_LIBRARY_PAGE_SIZE + index + 1).padStart(3, '0');
  }

  /** "Validated on 1 of 2" only when machines disagree; a single clear state needs no extra words. */
  protected romDetail(rom: RomSummary): string | null {
    return rom.state === 'MIXED' ? `Validated on ${rom.validated} of ${rom.total}` : null;
  }

  protected closePanel(): void {
    this.panelOpen.set(false);
    this.filterBar().focusFiltersButton();
  }

  onPreviousPage() {
    this.pageChange.emit(this.page() - 1);
  }

  onNextPage() {
    this.pageChange.emit(this.page() + 1);
  }
}
