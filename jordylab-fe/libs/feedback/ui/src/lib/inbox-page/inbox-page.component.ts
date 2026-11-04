import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FeedbackState, InboxStore } from '@jordylab-fe/feedback/api';
import { InboxPageViewComponent, TicketAcceptInput, TicketTextsInput } from './inbox-page-view.component';

@Component({
  selector: 'lib-inbox-page',
  standalone: true,
  imports: [InboxPageViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <lib-inbox-page-view
      [tickets]="tickets()"
      [loading]="loading()"
      [error]="error()"
      [actioningId]="actioningId()"
      [filter]="filter()"
      (setFilter)="onSetFilter($event)"
      (backlog)="onBacklog($event)"
      (reject)="onReject($event)"
      (accept)="onAccept($event)"
      (saveTexts)="onSaveTexts($event)"
      (reload)="onReload()"
      (dismissError)="onDismissError()"
    />
  `,
})
export class InboxPageComponent {
  readonly #store = inject(InboxStore);

  readonly tickets = this.#store.tickets;
  readonly loading = this.#store.loading;
  readonly error = this.#store.error;
  readonly actioningId = this.#store.actioningId;
  readonly filter = this.#store.filter;

  onSetFilter(filter: FeedbackState | null): void {
    this.#store.setFilter(filter);
  }

  onBacklog(id: string): void {
    this.#store.moveToBacklog(id);
  }

  onReject(id: string): void {
    this.#store.reject(id);
  }

  onAccept(input: TicketAcceptInput): void {
    this.#store.accept(input.id, input.privateNote);
  }

  onSaveTexts(input: TicketTextsInput): void {
    this.#store.updateTexts(input.id, input.reply, input.privateNote);
  }

  onReload(): void {
    this.#store.load();
  }

  onDismissError(): void {
    this.#store.dismissError();
  }
}
