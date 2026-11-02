import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import {
  FEEDBACK_STATE_LABELS,
  FeedbackState,
  InboxTicket,
} from '@jordylab-fe/feedback/api';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import {
  ErrorStateComponent,
  PageHeaderComponent,
  SkeletonRowsComponent,
} from '@jordylab-fe/shared/layout';

export interface TicketAcceptInput {
  id: string;
  privateNote: string | null;
}

export interface TicketTextsInput {
  id: string;
  reply: string;
  privateNote: string;
}

const TEXTAREA_CLASS =
  'mt-1 w-full rounded-xl border border-input bg-background px-3 py-2 text-foreground';

@Component({
  selector: 'lib-inbox-page-view',
  standalone: true,
  imports: [
    DatePipe,
    HlmBadgeDirective,
    HlmButtonDirective,
    ErrorStateComponent,
    PageHeaderComponent,
    SkeletonRowsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './inbox-page-view.component.html',
})
export class InboxPageViewComponent {
  tickets = input<InboxTicket[]>([]);
  loading = input(false);
  error = input<string | null>(null);
  actioningId = input<string | null>(null);
  filter = input<FeedbackState | null>(null);

  setFilter = output<FeedbackState | null>();
  backlog = output<string>();
  reject = output<string>();
  accept = output<TicketAcceptInput>();
  saveTexts = output<TicketTextsInput>();
  reload = output<void>();
  dismissError = output<void>();

  protected readonly textareaClass = TEXTAREA_CLASS;
  protected readonly stateLabels: Record<FeedbackState, string> = FEEDBACK_STATE_LABELS;
  protected readonly filters: readonly { id: FeedbackState | null; label: string }[] = [
    { id: null, label: 'All' },
    { id: 'NEW', label: 'New' },
    { id: 'BACKLOG', label: 'Backlog' },
    { id: 'ACCEPTED', label: 'Accepted' },
    { id: 'REJECTED', label: 'Rejected' },
  ];

  protected readonly acceptOpenId = signal<string | null>(null);
  protected readonly textsOpenId = signal<string | null>(null);
  protected readonly acceptNote = signal('');
  protected readonly replyDraft = signal('');
  protected readonly noteDraft = signal('');

  protected isActionable(ticket: InboxTicket): boolean {
    return ticket.state === 'NEW' || ticket.state === 'BACKLOG';
  }

  protected openAccept(ticket: InboxTicket): void {
    this.acceptOpenId.set(ticket.id);
    this.acceptNote.set('');
  }

  protected cancelAccept(): void {
    this.acceptOpenId.set(null);
  }

  protected confirmAccept(ticket: InboxTicket): void {
    const note = this.acceptNote().trim();
    this.accept.emit({ id: ticket.id, privateNote: note.length > 0 ? note : null });
    this.acceptOpenId.set(null);
  }

  protected openTexts(ticket: InboxTicket): void {
    this.textsOpenId.set(ticket.id);
    this.replyDraft.set(ticket.reply ?? '');
    this.noteDraft.set(ticket.privateNote ?? '');
  }

  protected cancelTexts(): void {
    this.textsOpenId.set(null);
  }

  protected saveTicketTexts(ticket: InboxTicket): void {
    this.saveTexts.emit({
      id: ticket.id,
      reply: this.replyDraft().trim(),
      privateNote: this.noteDraft().trim(),
    });
    this.textsOpenId.set(null);
  }

  protected senderLabel(ticket: InboxTicket): string {
    return ticket.sender ?? 'A guest';
  }
}
