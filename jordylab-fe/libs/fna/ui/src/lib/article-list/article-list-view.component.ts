import { Component, computed, input, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ArticleSummary } from '@jordylab-fe/fna/api';

interface ArticleDay {
  key: string;
  label: string;
  articles: ArticleSummary[];
}

@Component({
  selector: 'lib-article-list-view',
  standalone: true,
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './article-list-view.component.html',
})
export class ArticleListViewComponent {
  articles = input.required<ArticleSummary[]>();
  loading = input.required<boolean>();

  /** Articles bucketed by local calendar day, in the order they arrive (newest first). */
  protected readonly days = computed<ArticleDay[]>(() => {
    const days: ArticleDay[] = [];
    for (const article of this.articles()) {
      const date = new Date(article.publishedAt);
      const key = date.toDateString();
      let day = days.find((candidate) => candidate.key === key);
      if (!day) {
        day = { key, label: this.#dayLabel(date), articles: [] };
        days.push(day);
      }
      day.articles.push(article);
    }

    return days;
  });

  /** Feed accent: ECB in flare, Politico in iris, everything else neutral. */
  protected feedColor(feedName: string): string {
    if (feedName === 'ECB Press Releases') {
      return 'text-primary';
    }
    if (feedName === 'Politico Europe') {
      return 'text-iris';
    }

    return 'text-foreground';
  }

  #dayLabel(date: Date): string {
    const startOfDay = (value: Date) =>
      new Date(
        value.getFullYear(),
        value.getMonth(),
        value.getDate(),
      ).getTime();
    const daysAgo = Math.round(
      (startOfDay(new Date()) - startOfDay(date)) / 86_400_000,
    );
    const short = date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
    });

    if (daysAgo === 0) {
      return `Today · ${short}`;
    }
    if (daysAgo === 1) {
      return `Yesterday · ${short}`;
    }

    return short;
  }
}
