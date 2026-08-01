import { Component, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { ArticleSummary, FnaApiService } from '@jordylab-fe/fna/api';
import { ArticleListViewComponent } from './article-list-view.component';

@Component({
  selector: 'lib-article-list',
  standalone: true,
  imports: [ArticleListViewComponent],
  templateUrl: './article-list.component.html',
})
export class ArticleListComponent {
  #api = inject(FnaApiService);
  articles = signal<ArticleSummary[]>([]);
  error = signal<string | null>(null);
  loading = signal(true);

  constructor() {
    this.#api
      .getArticles()
      .pipe(
        catchError(() => {
          this.error.set('Failed to load articles.');
          this.loading.set(false);
          return of([]);
        })
      )
      .subscribe((articles) => {
        this.articles.set(articles);
        this.loading.set(false);
      });
  }
}
