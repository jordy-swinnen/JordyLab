import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { FeedbackApp, FeedbackTicket, TicketSubmission } from './feedback.models';

@Injectable({ providedIn: 'root' })
export class FeedbackApiService {
  #http = inject(HttpClient);

  getApps(): Observable<FeedbackApp[]> {
    return this.#http
      .get<{ apps: FeedbackApp[] }>('/api/feedback/apps')
      .pipe(map((response) => response.apps));
  }

  getOwnTickets(): Observable<FeedbackTicket[]> {
    return this.#http
      .get<{ tickets: FeedbackTicket[] }>('/api/feedback/tickets')
      .pipe(map((response) => response.tickets));
  }

  submit(submission: TicketSubmission): Observable<FeedbackTicket> {
    return this.#http.post<FeedbackTicket>('/api/feedback/tickets', submission);
  }
}
