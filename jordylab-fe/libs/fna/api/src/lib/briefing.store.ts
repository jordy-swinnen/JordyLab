import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, map, of } from 'rxjs';
import { Briefing } from './fna.models';

@Injectable({ providedIn: 'root' })
export class BriefingStore {
  readonly #http = inject(HttpClient);
  readonly #apiUrl = '/api/fna/briefing';
  readonly #triggerUrl = '/api/fna/briefing/trigger';

  readonly #briefing = signal<Briefing | null>(null);
  readonly #loading = signal(false);
  readonly #error = signal<string | null>(null);

  readonly briefing = this.#briefing.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();

  constructor() {
    this.load();
  }

  load(): void {
    this.#loading.set(true);
    this.#error.set(null);

    this.#http
      .get<Briefing>(this.#apiUrl, { observe: 'response' })
      .pipe(
        map((response) => (response.status === 204 ? null : response.body)),
        catchError(() => {
          this.#error.set('Failed to load briefing.');
          this.#loading.set(false);

          return of(null);
        })
      )
      .subscribe((briefing) => {
        this.#briefing.set(briefing);
        this.#loading.set(false);
      });
  }

  generate(): void {
    this.#loading.set(true);
    this.#error.set(null);

    this.#http
      .post<Briefing>(this.#triggerUrl, null)
      .pipe(
        catchError(() => {
          this.#error.set('Failed to generate briefing.');
          this.#loading.set(false);

          return of(null);
        })
      )
      .subscribe((briefing) => {
        this.#briefing.set(briefing);
        this.#loading.set(false);
      });
  }
}
