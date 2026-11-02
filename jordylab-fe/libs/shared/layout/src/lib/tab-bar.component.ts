import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';

export interface TabBarOption {
  readonly label: string;
  /** The SVG path data of a 24 by 24 outline icon. */
  readonly icon: string;
  readonly route: string;
  readonly active: boolean;
}

export interface TabBarItem {
  /** The module this tab stands for, such as Games or Finance. */
  readonly label: string;
  readonly icon: string;
  readonly active: boolean;
  /** What opens when the tab is tapped. A module with a single option goes straight to it. */
  readonly options: readonly TabBarOption[];
}

/**
 * The phone's bottom navigation: one tab per module. Tapping a module lifts its pages out of the bar as a sheet; a module
 * with only one page opens it directly. Leaves room for the system gesture area.
 */
@Component({
  selector: 'lib-tab-bar',
  standalone: true,
  imports: [NgTemplateOutlet, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown.escape)': 'close()' },
  template: `
    @if (openItem(); as module) {
      <button type="button" class="fixed inset-0 cursor-default bg-black/55" aria-label="Close menu" tabindex="-1" (click)="close()"></button>
      <ul
        id="tab-bar-options"
        [attr.aria-label]="module.label"
        data-testid="tab-bar-options"
        class="absolute inset-x-0 bottom-full m-0 flex list-none flex-col gap-1 rounded-t-[22px] border border-b-0 border-input bg-card px-3 pb-3 pt-4 shadow-modal"
      >
        @for (option of module.options; track option.route) {
          <li>
            <a
              class="flex min-h-12 items-center gap-3.5 rounded-xl px-3.5 text-[16px] outline-none focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary"
              [class]="option.active ? 'bg-background font-semibold text-foreground shadow-[inset_3px_0_0_hsl(var(--primary))]' : 'font-medium text-secondary-foreground'"
              [routerLink]="option.route"
              [attr.aria-current]="option.active ? 'page' : null"
              (click)="close()"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path [attr.d]="option.icon" />
              </svg>
              {{ option.label }}
            </a>
          </li>
        }
      </ul>
    }
    <nav aria-label="Main" class="relative flex min-h-[76px] border-t border-border bg-rail px-2 pb-[max(14px,env(safe-area-inset-bottom))] pt-1.5">
      @for (item of items(); track item.label) {
        @if (item.options.length === 1) {
          <a
            [class]="itemClass(item)"
            [routerLink]="item.options[0].route"
            [attr.aria-current]="item.active ? 'page' : null"
            (click)="close()"
          >
            <ng-container *ngTemplateOutlet="tabContent; context: { $implicit: item }" />
          </a>
        } @else {
          <button
            type="button"
            [class]="itemClass(item)"
            aria-haspopup="true"
            aria-controls="tab-bar-options"
            [attr.aria-expanded]="openLabel() === item.label"
            [attr.aria-current]="item.active ? 'page' : null"
            (click)="toggle(item.label)"
          >
            <ng-container *ngTemplateOutlet="tabContent; context: { $implicit: item }" />
          </button>
        }
      }
    </nav>

    <ng-template #tabContent let-item>
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path [attr.d]="item.icon" />
      </svg>
      <span>{{ item.label }}</span>
    </ng-template>
  `,
})
export class TabBarComponent {
  readonly items = input.required<readonly TabBarItem[]>();

  protected readonly openLabel = signal<string | null>(null);
  protected readonly openItem = computed(() => this.items().find((item) => item.label === this.openLabel()) ?? null);

  protected itemClass(item: TabBarItem): string {
    const shape =
      'flex min-h-11 flex-1 cursor-pointer flex-col items-center justify-center gap-1 bg-transparent text-[11.5px] outline-none focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary';

    return item.active || this.openLabel() === item.label
      ? `${shape} -mt-1.5 border-t-2 border-primary pt-1 font-semibold text-foreground`
      : `${shape} font-medium text-secondary-foreground`;
  }

  protected toggle(label: string): void {
    this.openLabel.set(this.openLabel() === label ? null : label);
  }

  protected close(): void {
    this.openLabel.set(null);
  }
}
