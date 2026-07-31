import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FnaApiService } from '@jordylab-fe/fna/api';
import { ArticleListViewComponent } from './article-list-view.component';

@Component({
  selector: 'lib-article-list',
  standalone: true,
  imports: [ArticleListViewComponent],
  templateUrl: './article-list.component.html',
})
export class ArticleListComponent {
  #api = inject(FnaApiService);
  articles = toSignal(this.#api.getArticles(), { initialValue: [] });
}
