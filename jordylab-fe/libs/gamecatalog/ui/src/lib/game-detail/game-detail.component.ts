import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { catchError, of } from 'rxjs';
import { GameCatalogApiService, GameDetail } from '@jordylab-fe/gamecatalog/api';
import { GameDetailViewComponent } from './game-detail-view.component';

@Component({
  selector: 'lib-game-detail',
  standalone: true,
  imports: [GameDetailViewComponent],
  templateUrl: './game-detail.component.html',
})
export class GameDetailComponent {
  #route = inject(ActivatedRoute);
  #api = inject(GameCatalogApiService);

  game = signal<GameDetail | null>(null);
  loading = signal(true);
  notFound = signal(false);
  error = signal<string | null>(null);

  constructor() {
    const id = this.#route.snapshot.paramMap.get('id') ?? '';
    this.#api
      .getGame(id)
      .pipe(
        catchError((httpError: HttpErrorResponse) => {
          if (httpError.status === 404) {
            this.notFound.set(true);
          } else {
            this.error.set('Failed to load the game.');
          }
          this.loading.set(false);
          return of(null);
        })
      )
      .subscribe((game) => {
        if (game) {
          this.game.set(game);
        }
        this.loading.set(false);
      });
  }
}
