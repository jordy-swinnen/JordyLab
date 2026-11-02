import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { FeedbackState, InboxTicket } from './feedback.models';

@Injectable({ providedIn: 'root' })
export class InboxApiService {
  #http = inject(HttpClient);

  getInbox(state?: FeedbackState): Observable<InboxTicket[]> {
    let params = new HttpParams();
    if (state) {
      params = params.set('state', state);
    }

    return this.#http
      .get<{ tickets: InboxTicket[] }>('/api/feedback/inbox', { params })
      .pipe(map((response) => response.tickets));
  }

  moveToBacklog(id: string): Observable<InboxTicket> {
    return this.#http.post<InboxTicket>(`/api/feedback/inbox/${id}/backlog`, {});
  }

  reject(id: string): Observable<InboxTicket> {
    return this.#http.post<InboxTicket>(`/api/feedback/inbox/${id}/reject`, {});
  }

  accept(id: string, privateNote: string | null): Observable<InboxTicket> {
    return this.#http.post<InboxTicket>(`/api/feedback/inbox/${id}/accept`, { privateNote });
  }

  updateTexts(id: string, reply: string, privateNote: string): Observable<InboxTicket> {
    return this.#http.put<InboxTicket>(`/api/feedback/inbox/${id}/texts`, { reply, privateNote });
  }
}
