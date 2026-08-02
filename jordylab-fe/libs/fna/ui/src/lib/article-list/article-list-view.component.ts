import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ArticleSummary } from '@jordylab-fe/fna/api';

@Component({
  selector: 'lib-article-list-view',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './article-list-view.component.html',
})
export class ArticleListViewComponent {
  articles = input.required<ArticleSummary[]>();
  loading = input.required<boolean>();
}
