import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** LibBot's mark: a four-point sparkle with a small companion, drawn with the current text colour. */
@Component({
  selector: 'lib-sparkle-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex shrink-0', 'aria-hidden': 'true' },
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="currentColor"
      stroke="none"
    >
      <path d="M10 2.5l1.9 5.6 5.6 1.9-5.6 1.9L10 17.5l-1.9-5.6L2.5 10l5.6-1.9L10 2.5z" />
      <path d="M18.5 13.5l.9 2.6 2.6.9-2.6.9-.9 2.6-.9-2.6-2.6-.9 2.6-.9.9-2.6z" />
    </svg>
  `,
})
export class SparkleIconComponent {
  size = input(20);
}
