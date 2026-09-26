import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { GameDetailStore } from '@jordylab-fe/gamecatalog/api';
import { GameDetailViewComponent } from './game-detail-view.component';

@Component({
  selector: 'lib-game-detail',
  standalone: true,
  imports: [GameDetailViewComponent],
  templateUrl: './game-detail.component.html',
})
export class GameDetailComponent {
  readonly #route = inject(ActivatedRoute);
  readonly #store = inject(GameDetailStore);

  readonly game = this.#store.game;
  readonly loading = this.#store.loading;
  readonly notFound = this.#store.notFound;
  readonly error = this.#store.error;

  constructor() {
    this.#store.load(this.#route.snapshot.paramMap.get('id') ?? '');
  }
}
