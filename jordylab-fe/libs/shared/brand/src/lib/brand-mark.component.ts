import { Component, input } from '@angular/core';

/** The JordyLab flask-J mark: a J whose hook holds two rising bubbles. */
@Component({
  selector: 'lib-brand-mark',
  standalone: true,
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 48 48"
      fill="none"
      aria-hidden="true"
      class="block flex-shrink-0"
    >
      <rect
        width="48"
        height="48"
        [attr.rx]="radius()"
        [attr.fill]="background()"
      />
      <path
        d="M30 10V27C30 32.5 26 36 21 36C17.5 36 15 34.5 13.5 32"
        [attr.stroke]="foreground()"
        stroke-width="6.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <circle cx="17" cy="21" r="3.6" [attr.fill]="foreground()" />
      <circle cx="21.5" cy="12.5" r="2.4" [attr.fill]="foreground()" />
    </svg>
  `,
})
export class BrandMarkComponent {
  size = input(34);
  radius = input(13);
  background = input('hsl(var(--primary))');
  foreground = input('hsl(var(--primary-foreground))');
}
