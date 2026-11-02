import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { AppUser, UserSession, UserStatus } from './settings.models';

@Injectable({ providedIn: 'root' })
export class SettingsUsersApiService {
  #http = inject(HttpClient);

  getUsers(status?: UserStatus | 'ALL'): Observable<AppUser[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }

    return this.#http
      .get<{ users: AppUser[] }>('/api/settings/users', { params })
      .pipe(map((response) => response.users));
  }

  getPendingCount(): Observable<number> {
    return this.#http
      .get<{ count: number }>('/api/settings/users/pending-count')
      .pipe(map((response) => response.count));
  }

  approve(id: string): Observable<void> {
    return this.#http.post<void>(`/api/settings/users/${id}/approve`, {});
  }

  reject(id: string): Observable<void> {
    return this.#http.post<void>(`/api/settings/users/${id}/reject`, {});
  }

  revoke(id: string): Observable<void> {
    return this.#http.post<void>(`/api/settings/users/${id}/revoke`, {});
  }

  resetPassword(id: string): Observable<string> {
    return this.#http
      .post<{ temporaryPassword: string }>(`/api/settings/users/${id}/reset-password`, {})
      .pipe(map((response) => response.temporaryPassword));
  }

  grantApp(id: string, appKey: string): Observable<void> {
    return this.#http.put<void>(`/api/settings/users/${id}/apps/${appKey}`, {});
  }

  removeApp(id: string, appKey: string): Observable<void> {
    return this.#http.delete<void>(`/api/settings/users/${id}/apps/${appKey}`);
  }

  setPersonalLimit(id: string, dailyLimit: number): Observable<void> {
    return this.#http.put<void>(`/api/settings/users/${id}/chat-limit`, { dailyLimit });
  }

  clearPersonalLimit(id: string): Observable<void> {
    return this.#http.delete<void>(`/api/settings/users/${id}/chat-limit`);
  }

  deletePerson(id: string): Observable<void> {
    return this.#http.delete<void>(`/api/settings/users/${id}`);
  }

  getSessions(id: string): Observable<UserSession[]> {
    return this.#http
      .get<{ sessions: UserSession[] }>(`/api/settings/users/${id}/sessions`)
      .pipe(map((response) => response.sessions));
  }

  endSession(id: string, sessionId: string, kind: UserSession['kind']): Observable<void> {
    return this.#http.delete<void>(`/api/settings/users/${id}/sessions/${sessionId}`, {
      params: { kind },
    });
  }

  endAllSessions(id: string): Observable<void> {
    return this.#http.post<void>(`/api/settings/users/${id}/sessions/end-all`, {});
  }
}
