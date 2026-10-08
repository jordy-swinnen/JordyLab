import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmSkeletonComponent } from '@spartan-ng/ui-skeleton-helm';
import { bannerUrl, coverUrl, GameDetail, MarkType, Place, ROM_STATUS_LABELS, RomStatus } from '@jordylab-fe/gamecatalog/api';
import { descriptionHeading, factSourcesLine } from './description-heading';
import { MarkChipComponent } from '../chips/mark-chip.component';
import { RomChipComponent } from '../chips/rom-chip.component';
import { MarkButtonsComponent } from '../marks/mark-buttons.component';
import { PlatformChipComponent } from '../chips/platform-chip.component';
import { SourceLabelComponent } from '../chips/source-label.component';
import { StatusChipComponent } from '../chips/status-chip.component';
import { coverInitials, coverPalette } from '../cover';

@Component({
  selector: 'lib-game-detail-view',
  standalone: true,
  imports: [
    RouterLink,
    HlmSkeletonComponent,
    MarkButtonsComponent,
    MarkChipComponent,
    PlatformChipComponent,
    RomChipComponent,
    SourceLabelComponent,
    StatusChipComponent,
  ],
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
  savingRomStatus = input<ReadonlySet<string>>(new Set());
  romStatusError = input<string | null>(null);
  markPending = input(false);
  markError = input<string | null>(null);

  refreshMetadata = output<void>();
  refreshEnrichment = output<void>();
  markToggle = output<MarkType>();
  romStatusChange = output<{ installationId: string; status: RomStatus }>();
  romStatusErrorDismiss = output<void>();

  protected readonly coverUrl = coverUrl;
  protected readonly bannerUrl = bannerUrl;
  protected readonly initials = coverInitials;
  protected readonly palette = coverPalette;

  /** Labels of the hosts holding an installed copy: the display name an admin chose, else the hostname. */
  protected hostLabels(game: GameDetail): string[] {
    return [
      ...new Set(
        game.places.filter((place) => place.kind === 'HOST_COPY' && place.installed).map((place) => place.label),
      ),
    ];
  }

  protected familyOwners(game: GameDetail): string | null {
    const owners = game.places.find((place) => place.kind === 'STEAM_LIBRARY' && place.familyOwners)?.familyOwners;

    return owners ?? null;
  }

  /** The copies found by an emulation scan: the only places that have a ROM status to give. */
  protected emulatedCopies(game: GameDetail): Place[] {
    return game.places.filter((place) => place.kind === 'HOST_COPY' && place.romStatus !== null);
  }

  protected readonly romStatuses = (['UNKNOWN', 'VALIDATED', 'BROKEN'] as RomStatus[]).map((value) => ({
    value,
    label: ROM_STATUS_LABELS[value],
  }));

  protected readonly descriptionHeading = descriptionHeading;
  protected readonly factSourcesLine = factSourcesLine;

  protected isSteam(game: GameDetail): boolean {
    return game.platforms.some((platform) => platform.name === 'Steam');
  }
}
