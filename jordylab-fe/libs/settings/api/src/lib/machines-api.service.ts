import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { CreatedMachine, Machine } from './machines.models';

/** Admin machine tokens for the scanner (spec 019 US5). */
@Injectable({ providedIn: 'root' })
export class MachinesApiService {
  #http = inject(HttpClient);

  getMachines(): Observable<Machine[]> {
    return this.#http
      .get<{ machines: Machine[] }>('/api/identity/machines')
      .pipe(map((response) => response.machines));
  }

  createMachine(name: string): Observable<CreatedMachine> {
    return this.#http.post<CreatedMachine>('/api/identity/machines', { name });
  }

  revoke(id: string): Observable<void> {
    return this.#http.post<void>(`/api/identity/machines/${id}/revoke`, {});
  }
}
