import { Component, computed, effect, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  NavigationEnd,
  Router,
  RouterLink,
  RouterOutlet,
} from '@angular/router';
import { filter, map } from 'rxjs';
import {
  AuthService,
  BiometricUnlockService,
  UserMenuComponent,
} from '@jordylab-fe/shared/auth';
import {
  BrandMarkComponent,
  WordmarkComponent,
} from '@jordylab-fe/shared/brand';
import { TabBarComponent, TabBarItem } from '@jordylab-fe/shared/layout';
import { PendingCountBadgeComponent } from '@jordylab-fe/settings/nav';
import { NAVIGATION_GROUPS, SETTINGS_GROUP_LABEL, NavGroup, visibleNavigationGroups } from './navigation';
import {
  ApkDownloadService,
  AppLinkService,
  InstallPromptStore,
  PlatformService,
  ShareTargetService,
  UpdateCheckStore,
} from '@jordylab-fe/shared/platform/api';
import {
  AndroidAppQrEntryComponent,
  InstallDownloadStatus,
  InstallPromptComponent,
  UpdateAvailableBannerComponent,
  UpdateRequiredComponent,
} from '@jordylab-fe/shared/platform/ui';

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
    UpdateRequiredComponent,
    UpdateAvailableBannerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './app.html',
})
export class App {
  #auth = inject(AuthService);
  #router = inject(Router);
  #apkDownload = inject(ApkDownloadService);
  #updateCheck = inject(UpdateCheckStore);
  #appLink = inject(AppLinkService);
  #shareTarget = inject(ShareTargetService);
  #biometricUnlock = inject(BiometricUnlockService);
  protected readonly platform = inject(PlatformService);
  protected readonly installPrompt = inject(InstallPromptStore);

  username = this.#auth.username;
  hasAppRole = this.#auth.hasAppRole;
  isAdmin = this.#auth.isAdmin;

  protected readonly showQrEntry = signal(false);
  protected readonly installStatus = signal<InstallDownloadStatus>('idle');
  protected readonly latestRelease = this.#updateCheck.latest;

  constructor() {
    this.installPrompt.suppressBrowserInstallPrompt();
    this.#updateCheck.listenForResume();
    // The release endpoint needs a signed-in admin/guest, so ask once the session exists (login, fingerprint
    // unlock or restored session) — asking at start-up only ever got a 401 on a cold start.
    effect(() => {
      if (this.hasAppRole()) {
        void this.#updateCheck.checkForUpdate();
      }
    });
    this.#appLink.listen();
    this.#shareTarget.listen();
    void this.#restoreNativeSession();
  }

  /**
   * Native cold start: there is no SSO cookie to restore a session from, so when the user opted into fingerprint
   * unlock, ask for it straight away instead of leaving them on the login page (spec 007 US4-1).
   */
  async #restoreNativeSession(): Promise<void> {
    if (!this.platform.isNative()) {
      return;
    }
    try {
      const authenticated = await this.#auth.init();
      if (!authenticated && (await this.#biometricUnlock.isEnabled())) {
        await this.#biometricUnlock.unlock();
      }
    } catch (error) {
      // Never leave an unhandled rejection: the login page's own buttons stay available as the fallback.
      console.error('Restoring the native session failed', error);
    }
  }

  async onInstallDownload(): Promise<void> {
    this.installStatus.set('preparing');
    try {
      const downloadUrl = await this.#apkDownload.resolveLatestDownloadUrl();
      window.location.assign(downloadUrl);
      this.installStatus.set('started');
    } catch {
      this.installStatus.set('failed');
    }
  }

  async onUpdateDownload(): Promise<void> {
    const release = this.latestRelease();
    if (!release) {
      return;
    }
    window.location.href = await this.#apkDownload.resolveDownloadUrl(release.id);
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
    // Explicit wipe, not left to the next failed refresh (FR-012) — a no-op when biometric
    // unlock was never enabled.
    await this.#biometricUnlock.disable();
    await this.#auth.logout();
  }
}
