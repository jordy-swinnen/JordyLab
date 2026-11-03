import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { MachinesApiService } from './machines-api.service';
import { CreatedMachine, Machine } from './machines.models';

@Injectable({ providedIn: 'root' })
export class MachinesStore {
  #api = inject(MachinesApiService);

  readonly #machines = signal<Machine[]>([]);
  readonly #loading = signal(true);
  readonly #error = signal<string | null>(null);
  readonly #creating = signal(false);
  readonly #created = signal<CreatedMachine | null>(null);

  readonly machines = this.#machines.asReadonly();
  readonly loading = this.#loading.asReadonly();
  readonly error = this.#error.asReadonly();
  readonly creating = this.#creating.asReadonly();
  readonly created = this.#created.asReadonly();

  constructor() {
    this.load();
  }

  load(): void {
    this.#loading.set(true);
    this.#error.set(null);
    this.#api
      .getMachines()
      .pipe(
        catchError(() => {
          this.#error.set('Failed to load machines.');

          return of<Machine[]>([]);
        }),
      )
      .subscribe((machines) => {
        this.#machines.set(machines);
        this.#loading.set(false);
      });
  }

  create(name: string): void {
    if (this.#creating()) {
      return;
    }
    this.#creating.set(true);
    this.#error.set(null);
    this.#api
      .createMachine(name)
      .pipe(
        catchError((error: unknown) => {
          this.#error.set(this.#reasonMessage(error, 'Failed to create the machine.'));

          return of<CreatedMachine | null>(null);
        }),
      )
      .subscribe((created) => {
        this.#creating.set(false);
        if (created) {
          this.#created.set(created);
          this.load();
        }
      });
  }

  revoke(id: string): void {
    this.#error.set(null);
    this.#api.revoke(id).subscribe({
      next: () => this.load(),
      error: (error: unknown) => {
        this.#error.set(this.#reasonMessage(error, 'Failed to revoke the machine.'));
      },
    });
  }

  dismissCreated(): void {
    this.#created.set(null);
  }

  #reasonMessage(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }
    const reason = (error.error as { reason?: string } | null)?.reason;
    switch (reason) {
      case 'ALREADY_REVOKED':
        return 'That machine token is already revoked.';
      case 'MACHINE_NOT_FOUND':
        return 'That machine no longer exists.';
      default:
        return fallback;
    }
  }
}
