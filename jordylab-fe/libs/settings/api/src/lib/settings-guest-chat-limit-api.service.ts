import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GuestChatLimit } from './settings.models';

@Injectable({ providedIn: 'root' })
export class SettingsGuestChatLimitApiService {
  #http = inject(HttpClient);

  getDefault(): Observable<GuestChatLimit> {
    return this.#http.get<GuestChatLimit>('/api/settings/guest-chat-limit');
  }

  saveDefault(dailyLimit: number): Observable<GuestChatLimit> {
    return this.#http.put<GuestChatLimit>('/api/settings/guest-chat-limit', { dailyLimit });
  }
}
