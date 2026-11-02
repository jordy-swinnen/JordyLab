import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { GuestChatLimit } from '@jordylab-fe/settings/api';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

/**
 * The default daily chat limit for guests (spec 014 US3). It applies to every guest without a personal limit, from
 * their next message. 0 is a real value that blocks chat; the environment value is only the starting default.
 */
@Component({
  selector: 'lib-guest-chat-limit-card',
  standalone: true,
  imports: [HlmButtonDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <section class="panel mt-8 px-6 py-5">
      <p class="eyebrow">Guest chat limit</p>
      <p class="mt-1 text-sm text-muted-foreground">
        Daily messages per guest. 0 blocks chat. The admin is never limited.
      </p>
      @if (loading()) {
        <p class="mt-3 text-sm text-muted-foreground">Loading…</p>
      } @else if (limit(); as current) {
        <div class="mt-4 flex flex-wrap items-end gap-3">
          <label class="text-sm">
            <span class="mono-label block">Daily limit</span>
            <input
              type="number"
              min="0"
              step="1"
              [value]="current.dailyLimit"
              #limitInput
              class="mt-1 w-28 rounded-lg border border-border bg-background px-3 py-2 font-mono text-sm"
              data-testid="guest-limit-input"
            />
          </label>
          <button
            hlmBtn
            variant="default"
            [disabled]="saving()"
            (click)="onSave(limitInput.valueAsNumber)"
            data-testid="save-guest-limit"
          >
            Save
          </button>
          <span class="text-xs text-muted-foreground" data-testid="guest-limit-source">
            {{ sourceLabel(current.source) }}
          </span>
        </div>
      }
      @if (error()) {
        <p class="mt-3 text-sm text-destructive">{{ error() }}</p>
      }
    </section>
  `,
})
export class GuestChatLimitCardComponent {
  limit = input<GuestChatLimit | null>(null);
  loading = input(false);
  saving = input(false);
  error = input<string | null>(null);

  saveDefault = output<number>();

  onSave(dailyLimit: number): void {
    this.saveDefault.emit(dailyLimit);
  }

  sourceLabel(source: GuestChatLimit['source']): string {
    switch (source) {
      case 'DEFAULT_SAVED':
        return 'Saved default';
      case 'ENVIRONMENT':
        return 'Environment default';
      default:
        return source;
    }
  }
}
