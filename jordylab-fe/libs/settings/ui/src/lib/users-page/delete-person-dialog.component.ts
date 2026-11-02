import { Component, input, output, ChangeDetectionStrategy } from '@angular/core';
import { AppUser } from '@jordylab-fe/settings/api';
import { BrnAlertDialogImports } from '@spartan-ng/brain/alert-dialog';
import { BrnDialogCloseDirective } from '@spartan-ng/brain/dialog';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

/**
 * Deleting a person is irreversible and ends every session they have (spec 014 US4, FR-014), so it always asks first.
 * The alert-dialog brain gives the confirmation dialog its focus trap, Escape handling and screen-reader labelling.
 */
@Component({
  selector: 'lib-delete-person-dialog',
  standalone: true,
  imports: [BrnAlertDialogImports, BrnDialogCloseDirective, HlmButtonDirective],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <brn-alert-dialog>
      <button
        brnAlertDialogTrigger
        hlmBtn
        class="h-11"
        variant="outline"
        [disabled]="busy()"
        [attr.data-testid]="'delete-user-' + user().id"
      >
        Delete
      </button>
      <brn-alert-dialog-overlay />
      <div *brnAlertDialogContent="let ctx" class="panel w-full max-w-md p-7">
        <h3 brnAlertDialogTitle class="text-lg text-foreground">
          Delete {{ displayName() }}?
        </h3>
        <p brnAlertDialogDescription class="mt-3 text-sm text-muted-foreground">
          Every session ends, including the phone app, and their chat usage, game votes and limits are
          removed. This cannot be undone.
        </p>
        <div class="mt-6 flex justify-end gap-3">
          <button
            brnDialogClose
            hlmBtn
            class="h-11"
            variant="outline"
            [attr.data-testid]="'cancel-delete-' + user().id"
            (click)="ctx.close()"
          >
            Cancel
          </button>
          <button
            hlmBtn
            class="h-11"
            variant="destructive"
            [attr.data-testid]="'confirm-delete-' + user().id"
            (click)="confirm.emit(); ctx.close()"
          >
            Delete person
          </button>
        </div>
      </div>
    </brn-alert-dialog>
  `,
})
export class DeletePersonDialogComponent {
  user = input.required<AppUser>();
  busy = input(false);

  confirm = output<void>();

  displayName(): string {
    const user = this.user();
    const name = `${user.firstName} ${user.lastName}`.trim();

    return name.length > 0 ? name : user.email;
  }
}
