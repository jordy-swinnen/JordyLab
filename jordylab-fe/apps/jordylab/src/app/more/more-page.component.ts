import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '@jordylab-fe/shared/auth';
import { PageHeaderComponent } from '@jordylab-fe/shared/layout';
import { visibleNavigationGroups } from '../navigation';

/** The phone's "More" tab: whatever does not fit the tab bar, limited to what this person's role may open. */
@Component({
  selector: 'app-more-page',
  standalone: true,
  imports: [RouterLink, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <lib-page-header title="More" />
    <ul class="mt-6 flex flex-col gap-2.5">
      @for (item of items(); track item.path) {
        <li>
          <a
            [routerLink]="item.path"
            class="flex min-h-14 items-center gap-3.5 rounded-xl border border-border bg-card px-4 py-3 text-[16px] font-semibold text-foreground"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path [attr.d]="item.icon" />
            </svg>
            {{ item.label }}
          </a>
        </li>
      }
    </ul>
  `,
})
export class MorePageComponent {
  readonly #auth = inject(AuthService);

  protected readonly items = computed(() =>
    visibleNavigationGroups(this.#auth.isAdmin())
      .flatMap((group) => group.items)
      .filter((item) => item.inMore),
  );
}
