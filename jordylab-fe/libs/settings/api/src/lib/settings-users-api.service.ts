import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { AppUser, UserSession, UserStatus } from './settings.models';

/** Admin user management over the identity API (spec 019). */
@Injectable({ providedIn: 'root' })
export class SettingsUsersApiService {
  #http = inject(HttpClient);

  getUsers(status?: UserStatus | 'ALL'): Observable<AppUser[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }

    return this.#http
      .get<{ users: AppUser[] }>('/api/identity/users', { params })
      .pipe(map((response) => response.users));
  }

  getPendingCount(): Observable<number> {
    return this.#http
      .get<{ pendingCount: number }>('/api/identity/users/pending-count')
      .pipe(map((response) => response.pendingCount));
  }

  approve(id: string, role?: 'admin' | 'guest'): Observable<void> {
    return this.#http.post<void>(`/api/identity/users/${id}/approve`, { role: role ?? null });
  }

  reject(id: string): Observable<void> {
    return this.#http.post<void>(`/api/identity/users/${id}/reject`, {});
  }

  revoke(id: string): Observable<void> {
    return this.#http.post<void>(`/api/identity/users/${id}/revoke`, {});
  }

  resetPassword(id: string): Observable<{ temporaryPassword: string }> {
    return this.#http.post<{ temporaryPassword: string }>(
      `/api/identity/users/${id}/reset-password`,
      {},
    );
  }

  approveResetRequest(id: string, requestId: string): Observable<void> {
    return this.#http.post<void>(
      `/api/identity/users/${id}/reset-requests/${requestId}/approve`,
      {},
    );
  }

  rejectResetRequest(id: string, requestId: string): Observable<void> {
    return this.#http.post<void>(
      `/api/identity/users/${id}/reset-requests/${requestId}/reject`,
      {},
    );
  }

  grantApp(id: string, appKey: string): Observable<void> {
    return this.#http.put<void>(`/api/identity/users/${id}/apps/${appKey}`, {});
  }

  removeApp(id: string, appKey: string): Observable<void> {
    return this.#http.delete<void>(`/api/identity/users/${id}/apps/${appKey}`);
  }

  getSessions(id: string): Observable<UserSession[]> {
    return this.#http
      .get<{ sessions: UserSession[] }>(`/api/identity/users/${id}/sessions`)
      .pipe(map((response) => response.sessions));
  }

  endSession(id: string, sessionId: string): Observable<void> {
    return this.#http.post<void>(`/api/identity/users/${id}/sessions/${sessionId}/end`, {});
  }

  endAllSessions(id: string): Observable<void> {
    return this.#http.post<void>(`/api/identity/users/${id}/sessions/end-all`, {});
  }

  deletePerson(id: string): Observable<void> {
    return this.#http.delete<void>(`/api/identity/users/${id}`);
  }
}
