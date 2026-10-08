import { ChangeDetectionStrategy, Component, effect, ElementRef, input, output, viewChild } from '@angular/core';

/**
 * A modal question with two answers, on the browser's own {@code <dialog>}: focus is trapped inside, Escape cancels, and the
 * page behind is inert. The message is projected so a caller can state a number or a warning in its own words.
 */
@Component({
  selector: 'lib-confirm-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './confirm-dialog.component.html',
})
export class ConfirmDialogComponent {
  protected readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  open = input.required<boolean>();
  heading = input.required<string>();
  confirmLabel = input('Confirm');
  cancelLabel = input('Cancel');
  /** Styles the confirm button as a warning when the action costs money or hides things. */
  warning = input(false);

  confirmed = output<void>();
  cancelled = output<void>();

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      if (this.open() && !element.open) {
        element.showModal();
      } else if (!this.open() && element.open) {
        element.close();
      }
    });
  }

  protected onNativeCancel(event: Event): void {
    event.preventDefault();
    this.cancelled.emit();
  }
}
