import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface TabBarItem {
  readonly label: string;
  /** The SVG path data of a 24 by 24 outline icon. */
  readonly icon: string;
  readonly route: string;
  readonly active: boolean;
}

/** The phone's bottom navigation: three or four destinations chosen by the shell, with room for the system gesture area. */
@Component({
  selector: 'lib-tab-bar',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav aria-label="Main" class="flex min-h-[76px] border-t border-border bg-rail px-2 pb-[max(14px,env(safe-area-inset-bottom))] pt-1.5">
      @for (item of items(); track item.route) {
        <a
          class="flex min-h-11 flex-1 flex-col items-center justify-center gap-1 text-[11.5px] outline-none focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary"
          [class]="item.active ? '-mt-1.5 border-t-2 border-primary pt-1 font-semibold text-foreground' : 'font-medium text-secondary-foreground'"
          [routerLink]="item.route"
          [attr.aria-current]="item.active ? 'page' : null"
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path [attr.d]="item.icon" />
          </svg>
          {{ item.label }}
        </a>
      }
    </nav>
  `,
})
export class TabBarComponent {
  items = input.required<readonly TabBarItem[]>();
}
