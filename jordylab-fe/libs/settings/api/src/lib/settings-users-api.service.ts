import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { AppUser, UserStatus } from './settings.models';

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
}
