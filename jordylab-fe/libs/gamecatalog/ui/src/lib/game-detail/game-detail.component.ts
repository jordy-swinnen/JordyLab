import { Component, effect, inject, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { GameDetailStore, SwitchGameFormat } from '@jordylab-fe/gamecatalog/api';
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
  readonly #router = inject(Router);
  readonly #store = inject(GameDetailStore);

  readonly game = this.#store.game;
  readonly loading = this.#store.loading;
  readonly notFound = this.#store.notFound;
  readonly error = this.#store.error;
  readonly refreshingMetadata = this.#store.refreshingMetadata;
  readonly refreshingEnrichment = this.#store.refreshingEnrichment;
  readonly savingSwitch = this.#store.savingSwitch;
  readonly relinkCandidates = this.#store.relinkCandidates;
  /** Refresh, regenerate and Switch management call admin-only endpoints (006 FR-002); guests get read-only. */
  readonly canAdminister = inject(AuthService).isAdmin;

  constructor() {
    this.#store.load(this.#route.snapshot.paramMap.get('id') ?? '');
    effect(() => {
      if (this.#store.removed()) {
        void this.#router.navigateByUrl('/games/grid');
      }
    });
  }

  onRefreshMetadata(): void {
    this.#store.refreshMetadata();
  }

  onRefreshEnrichment(): void {
    this.#store.refreshEnrichment();
  }

  onChangeSwitchFormat(format: SwitchGameFormat): void {
    this.#store.changeSwitchFormat(format);
  }

  onSearchRelink(query: string): void {
    this.#store.searchRelinkCandidates(query);
  }

  onRelink(igdbGameId: number): void {
    this.#store.relinkSwitchGame(igdbGameId);
  }

  onRemoveSwitch(): void {
    this.#store.removeSwitchGame();
  }
}
