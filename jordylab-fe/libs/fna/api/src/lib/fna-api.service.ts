import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ArticleSummary, Briefing, PortfolioPosition } from './fna.models';

@Injectable({ providedIn: 'root' })
export class FnaApiService {
  #http = inject(HttpClient);

  getArticles(): Observable<ArticleSummary[]> {
    return this.#http.get<ArticleSummary[]>('/api/fna/articles');
  }

  getPortfolio(): Observable<PortfolioPosition[]> {
    return this.#http.get<PortfolioPosition[]>('/api/fna/portfolio');
  }

  upsertPosition(
    ticker: string,
    shares: number
  ): Observable<PortfolioPosition> {
    return this.#http.put<PortfolioPosition>(
      `/api/fna/portfolio/${ticker}`,
      null,
      { params: { shares } }
    );
  }

  removePosition(id: string): Observable<void> {
    return this.#http.delete<void>(`/api/fna/portfolio/${id}`);
  }

  getLatestBriefing(): Observable<Briefing | null> {
    return this.#http
      .get<Briefing>('/api/fna/briefing', { observe: 'response' })
      .pipe(map((response) => (response.status === 204 ? null : response.body)));
  }

  triggerBriefing(): Observable<Briefing> {
    return this.#http.post<Briefing>('/api/fna/briefing/trigger', null);
  }
}
