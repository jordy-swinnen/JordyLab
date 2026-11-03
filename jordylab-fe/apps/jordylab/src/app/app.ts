import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService, UserMenuComponent } from '@jordylab-fe/shared/auth';
import { BrandMarkComponent, WordmarkComponent } from '@jordylab-fe/shared/brand';
import { TabBarComponent, TabBarItem } from '@jordylab-fe/shared/layout';
import { PendingCountBadgeComponent } from '@jordylab-fe/settings/nav';
import { NAVIGATION_GROUPS, SETTINGS_GROUP_LABEL, NavGroup, visibleNavigationGroups } from './navigation';
import { InstallPromptStore, PlatformService } from '@jordylab-fe/shared/platform/api';
import { AndroidAppQrEntryComponent, InstallPromptComponent } from '@jordylab-fe/shared/platform/ui';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    UserMenuComponent,
    BrandMarkComponent,
    WordmarkComponent,
    PendingCountBadgeComponent,
    TabBarComponent,
    InstallPromptComponent,
    AndroidAppQrEntryComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './app.html',
})
export class App {
  #auth = inject(AuthService);
  #router = inject(Router);
  protected readonly platform = inject(PlatformService);
  protected readonly installPrompt = inject(InstallPromptStore);

  username = this.#auth.username;
  hasAppRole = this.#auth.hasAppRole;
  isAdmin = this.#auth.isAdmin;

  protected readonly showQrEntry = signal(false);

  constructor() {
    // Captures the browser's install event once; the one-time sheet and Settings → App card both use it (spec 018).
    this.installPrompt.captureInstallPrompt();
  }

  protected readonly isSettingsGroup = (group: NavGroup): boolean =>
    group.label === SETTINGS_GROUP_LABEL;

  #url = toSignal(
    this.#router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.#router.url },
  );

  protected readonly visibleGroups = computed(() =>
    visibleNavigationGroups(this.isAdmin(), this.#auth.hasGameCatalogAccess()),
  );

  protected readonly tabs = computed<TabBarItem[]>(() => {
    const url = this.#url();

    return this.visibleGroups().map((group) => {
      const options = group.items.map((item) => ({
        label: item.label,
        icon: item.icon,
        route: item.path,
        active: item.isActive(url),
      }));

      return { label: group.tab.label, icon: group.tab.icon, options, active: options.some((option) => option.active) };
    });
  });

  protected readonly activePath = computed(() => {
    const url = this.#url();
    return (
      NAVIGATION_GROUPS
        .flatMap((group) => group.items)
        .find((item) => item.isActive(url))?.path ?? null
    );
  });

  async onLogout(): Promise<void> {
    await this.#auth.logout();
    await this.#router.navigateByUrl('/login');
  }
}
