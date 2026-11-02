import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { AppUser } from '@jordylab-fe/settings/api';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

/**
 * One person's daily chat limit (spec 014 US3). It shows the limit in force and where it comes from, and lets the admin
 * set a personal limit or go back to the default. The admin's own row never shows this control.
 */
@Component({
  selector: 'lib-person-chat-limit',
  standalone: true,
  imports: [HlmButtonDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @if (user().chatLimit; as chatLimit) {
      <div class="mt-4 border-t border-border/60 pt-4">
        <p class="mono-label">Daily chat limit</p>
        <div class="mt-2 flex flex-wrap items-end gap-3">
          <label class="text-sm">
            <span class="sr-only">Daily chat limit for {{ user().email }}</span>
            <input
              type="number"
              min="0"
              step="1"
              [value]="chatLimit.limit"
              #limitInput
              class="w-24 rounded-lg border border-border bg-background px-3 py-2 font-mono text-sm"
              [attr.data-testid]="'chat-limit-input-' + user().id"
            />
          </label>
          <button
            hlmBtn
            variant="outline"
            [disabled]="busy()"
            (click)="setPersonal.emit(limitInput.valueAsNumber)"
            [attr.data-testid]="'save-chat-limit-' + user().id"
          >
            Save
          </button>
          <button
            hlmBtn
            variant="ghost"
            [disabled]="busy()"
            (click)="clearPersonal.emit()"
            [attr.data-testid]="'clear-chat-limit-' + user().id"
          >
            Use default
          </button>
          <span class="text-xs text-muted-foreground">
            {{ sourceLabel(chatLimit.source) }}
          </span>
        </div>
      </div>
    }
  `,
})
export class PersonChatLimitComponent {
  user = input.required<AppUser>();
  busy = input(false);

  setPersonal = output<number>();
  clearPersonal = output<void>();

  sourceLabel(source: NonNullable<AppUser['chatLimit']>['source']): string {
    switch (source) {
      case 'PERSONAL':
        return 'Personal limit';
      case 'DEFAULT_SAVED':
        return 'Saved default';
      case 'ENVIRONMENT':
        return 'Environment default';
      default:
        return source;
    }
  }
}
