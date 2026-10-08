import { afterNextRender, ChangeDetectionStrategy, Component, ElementRef, inject, input, output } from '@angular/core';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';

/** The inline editor for the name an admin gives a machine. Saving a blank name brings the hostname back. */
@Component({
  selector: 'lib-host-name-editor',
  standalone: true,
  imports: [HlmButtonDirective, HlmInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './host-name-editor.component.html',
})
export class HostNameEditorComponent {
  readonly #host = inject<ElementRef<HTMLElement>>(ElementRef);

  hostname = input.required<string>();
  displayName = input<string | null>(null);
  saving = input(false);
  problem = input<string | null>(null);

  save = output<string>();
  dismiss = output<void>();

  protected readonly maxLength = 40;

  constructor() {
    afterNextRender(() => this.#host.nativeElement.querySelector('input')?.focus());
  }
}
