import { Component, computed, inject, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { GameDetailStore, MarkStore, MarkType, RomStatus } from '@jordylab-fe/gamecatalog/api';
import { AuthService } from '@jordylab-fe/shared/auth';
import { GameDetailViewComponent } from './game-detail-view.component';

@Component({
  selector: 'lib-game-detail',
  standalone: true,
  imports: [GameDetailViewComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './game-detail.component.html',
})
export class GameDetailComponent {
  readonly #route = inject(ActivatedRoute);
  readonly #store = inject(GameDetailStore);
  readonly #marks = inject(MarkStore);

  /** The loaded game with this session's mark change on top, so a tap shows at once. */
  readonly game = computed(() => {
    const loaded = this.#store.game();

    return loaded ? this.#marks.stateOf(loaded) : null;
  });
  readonly markPending = computed(() => {
    const id = this.#store.game()?.id;

    return id !== undefined && this.#marks.pending().has(id);
  });
  readonly markError = this.#marks.error;
  readonly loading = this.#store.loading;
  readonly notFound = this.#store.notFound;
  readonly error = this.#store.error;
  readonly refreshingMetadata = this.#store.refreshingMetadata;
  readonly refreshingEnrichment = this.#store.refreshingEnrichment;
  readonly savingRomStatus = this.#store.savingRomStatus;
  readonly romStatusError = this.#store.romStatusError;
  /** Refresh and regenerate call admin-only endpoints (006 FR-002); guests get read-only. */
  readonly canAdminister = inject(AuthService).isAdmin;

  constructor() {
    this.#store.load(this.#route.snapshot.paramMap.get('id') ?? '');
  }

  onRomStatusChanged(change: { installationId: string; status: RomStatus }): void {
    this.#store.setRomStatus(change.installationId, change.status);
  }

  onRomStatusErrorDismissed(): void {
    this.#store.dismissRomStatusError();
  }

  onMarkToggled(mark: MarkType): void {
    const game = this.game();
    if (game) {
      this.#marks.toggle(game, mark);
    }
  }

  onRefreshMetadata(): void {
    this.#store.refreshMetadata();
  }

  onRefreshEnrichment(): void {
    this.#store.refreshEnrichment();
  }
}
