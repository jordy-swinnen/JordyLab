import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

/** An on/off setting as a real switch. The button is a full 44 pixels tall for touch; the track inside is 27 by 46. */
@Component({
  selector: 'lib-switch',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      role="switch"
      class="inline-flex h-11 w-[52px] shrink-0 cursor-pointer items-center justify-center rounded-full outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-default disabled:opacity-50"
      [attr.aria-checked]="checked()"
      [attr.aria-label]="label()"
      [disabled]="disabled()"
      (click)="checkedChange.emit(!checked())"
    >
      <span class="relative block h-[27px] w-[46px] rounded-full transition-colors duration-200" [class]="trackClass()">
        <span class="absolute left-[3px] top-[3px] block h-[21px] w-[21px] rounded-full transition-transform duration-200" [class]="knobClass()"></span>
      </span>
    </button>
  `,
})
export class SwitchComponent {
  checked = input.required<boolean>();
  label = input.required<string>();
  disabled = input(false);

  checkedChange = output<boolean>();

  protected readonly trackClass = computed(() => (this.checked() ? 'bg-primary' : 'bg-input'));
  protected readonly knobClass = computed(() => (this.checked() ? 'translate-x-[19px] bg-primary-foreground' : 'bg-foreground'));
}
