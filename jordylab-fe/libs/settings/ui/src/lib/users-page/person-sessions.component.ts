import { DatePipe } from '@angular/common';
import { Component, input, output, signal, ChangeDetectionStrategy } from '@angular/core';
import { UserSession } from '@jordylab-fe/settings/api';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

/**
 * One person's sessions (spec 014 US5). Ending one signs out only that device; ending all also ends the scanner's
 * offline session. The confirmation for "end all" is asked by the container.
 */
@Component({
  selector: 'lib-person-sessions',
  standalone: true,
  imports: [DatePipe, HlmButtonDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="mt-4 border-t border-border/60 pt-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <p class="mono-label">Sessions</p>
        @if (confirmingEndAll()) {
          <div class="flex items-center gap-2">
            <button
              hlmBtn
              class="h-11"
              variant="outline"
              (click)="confirmingEndAll.set(false)"
              data-testid="cancel-end-all"
            >
              Cancel
            </button>
            <button
              hlmBtn
              class="h-11"
              variant="destructive"
              [disabled]="actioning()"
              (click)="confirmEndAll()"
              data-testid="confirm-end-all"
            >
              End every session
            </button>
          </div>
        } @else {
          <button
            hlmBtn
            class="h-11"
            variant="outline"
            [disabled]="actioning() || sessions().length === 0"
            (click)="confirmingEndAll.set(true)"
            data-testid="end-all-sessions"
          >
            End all
          </button>
        }
      </div>
      @if (loading()) {
        <p class="mt-2 text-sm text-muted-foreground">Loading…</p>
      } @else if (sessions().length === 0) {
        <p class="mt-2 text-sm text-muted-foreground">No active sessions.</p>
      } @else {
        <div class="mt-2 flex flex-col gap-2">
          @for (session of sessions(); track session.id) {
            <div class="flex flex-wrap items-center justify-between gap-3 text-sm">
              <div>
                <p class="text-foreground">
                  {{ session.kind === 'SCANNER' ? 'Scanner' : 'Web' }}
                  @if (session.offline) {
                    <span class="text-xs text-muted-foreground">(offline session)</span>
                  }
                </p>
                <p class="text-xs text-muted-foreground">
                  started {{ session.startedAt | date: 'MMM d, h:mm a' }} · last used
                  {{ session.lastAccessedAt | date: 'MMM d, h:mm a' }}
                </p>
              </div>
              <button
                hlmBtn
                class="h-11"
                variant="outline"
                [disabled]="actioning()"
                (click)="endOne.emit(session)"
                [attr.data-testid]="'end-session-' + session.id"
              >
                End session
              </button>
            </div>
          }
        </div>
      }
      @if (error()) {
        <p class="mt-2 text-sm text-destructive">{{ error() }}</p>
      }
    </div>
  `,
})
export class PersonSessionsComponent {
  sessions = input.required<UserSession[]>();
  loading = input(false);
  actioning = input(false);
  error = input<string | null>(null);

  endOne = output<UserSession>();
  endAll = output<void>();

  readonly confirmingEndAll = signal(false);

  confirmEndAll(): void {
    this.confirmingEndAll.set(false);
    this.endAll.emit();
  }
}
