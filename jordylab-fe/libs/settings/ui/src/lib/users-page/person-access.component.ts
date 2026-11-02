import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { AppAccess, AppUser } from '@jordylab-fe/settings/api';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

/**
 * The apps one person may use (spec 014 US1/US2). A grantable app toggles between Grant and Remove
 * access; an app that can never be granted (FNA, Settings) shows its reason and a disabled control,
 * so the panel explains the rule instead of hiding it.
 */
@Component({
  selector: 'lib-person-access',
  standalone: true,
  imports: [HlmButtonDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="mt-4 border-t border-border/60 pt-4">
      <p class="mono-label">Apps</p>
      <div class="mt-2 flex flex-col gap-2">
        @for (app of user().apps; track app.key) {
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="min-w-0">
              <p class="text-sm text-foreground">{{ app.name }}</p>
              @if (!app.grantable) {
                <p class="text-xs text-muted-foreground">{{ app.reason }}</p>
              }
            </div>
            @if (user().isAdmin && app.grantable) {
              <span
                class="text-xs text-muted-foreground"
                [attr.data-testid]="'admin-app-' + app.key"
              >
                Admin access
              </span>
            } @else if (app.grantable) {
              <button
                hlmBtn
                variant="outline"
                [disabled]="busy()"
                (click)="toggle(app)"
                [attr.data-testid]="'toggle-app-' + app.key"
              >
                {{ app.granted ? 'Remove access' : 'Grant' }}
              </button>
            } @else {
              <button
                hlmBtn
                variant="outline"
                disabled
                [attr.data-testid]="'disabled-app-' + app.key"
              >
                Not grantable
              </button>
            }
          </div>
        }
      </div>
    </div>
  `,
})
export class PersonAccessComponent {
  user = input.required<AppUser>();
  busy = input(false);

  grant = output<string>();
  remove = output<string>();

  toggle(app: AppAccess): void {
    if (app.granted) {
      this.remove.emit(app.key);

      return;
    }
    this.grant.emit(app.key);
  }
}
