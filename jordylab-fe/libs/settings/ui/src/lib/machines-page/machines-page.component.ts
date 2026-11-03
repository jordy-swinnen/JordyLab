import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { MachinesStore } from '@jordylab-fe/settings/api';
import { MachinesPageViewComponent } from './machines-page-view.component';

@Component({
  selector: 'lib-machines-page',
  standalone: true,
  imports: [MachinesPageViewComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <lib-machines-page-view
      [machines]="machines()"
      [loading]="loading()"
      [error]="error()"
      [creating]="creating()"
      [created]="created()"
      (create)="onCreate($event)"
      (revoke)="onRevoke($event)"
      (reload)="onReload()"
      (dismissCreated)="onDismissCreated()"
    />
  `,
})
export class MachinesPageComponent {
  readonly #store = inject(MachinesStore);

  readonly machines = this.#store.machines;
  readonly loading = this.#store.loading;
  readonly error = this.#store.error;
  readonly creating = this.#store.creating;
  readonly created = this.#store.created;

  onCreate(name: string): void {
    this.#store.create(name);
  }

  onRevoke(id: string): void {
    this.#store.revoke(id);
  }

  onReload(): void {
    this.#store.load();
  }

  onDismissCreated(): void {
    this.#store.dismissCreated();
  }
}
