import { Component, inject } from '@angular/core';
import { ArticleStore } from '@jordylab-fe/fna/api';
import { ArticleListViewComponent } from './article-list-view.component';

@Component({
  selector: 'lib-article-list',
  standalone: true,
  imports: [ArticleListViewComponent],
  templateUrl: './article-list.component.html',
})
export class ArticleListComponent {
  #store = inject(ArticleStore);

  articles = this.#store.articles;
  loading = this.#store.loading;
  error = this.#store.error;
}
