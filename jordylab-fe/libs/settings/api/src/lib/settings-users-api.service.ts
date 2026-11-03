import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { AppUser, UserStatus } from './settings.models';

/** Admin user management over the identity API (spec 019; was the Keycloak-backed settings API before). */
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
}
