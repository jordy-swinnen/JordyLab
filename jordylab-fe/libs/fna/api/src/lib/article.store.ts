import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, of, tap } from 'rxjs';
import { ArticleSummary } from './fna.models';

@Injectable({ providedIn: 'root' })
export class ArticleStore {
  readonly #http = inject(HttpClient);
  readonly #apiUrl = '/api/fna/articles';

  readonly #articles = signal<ArticleSummary[]>([]);
  readonly #loading = signal(true);
  readonly #error = signal<string | null>(null);

  readonly articles = this.#articles.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();

  readonly hasArticles = computed(() => this.#articles().length > 0);

  constructor() {
    this.load();
  }

  load(): void {
    this.#loading.set(true);
    this.#error.set(null);

    this.#http
      .get<ArticleSummary[]>(this.#apiUrl)
      .pipe(
        tap((articles) => this.#articles.set(articles)),
        catchError(() => {
          this.#error.set('Failed to load articles.');

          return of([]);
        })
      )
      .subscribe(() => this.#loading.set(false));
  }
}
