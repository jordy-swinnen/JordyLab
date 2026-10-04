import { Component, computed, effect, inject, signal } from '@angular/core';
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
import { PendingCountBadgeComponent } from '@jordylab-fe/settings/nav';
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

interface NavItem {
  label: string;
  path: string;
  icon: string;
  /** Extra route prefixes that should keep this item highlighted (e.g. game detail under Library). */
  isActive: (url: string) => boolean;
  /** Omit for admin-or-guest; 'admin' hides the item from guests (mirrors the backend access matrix). */
  requiredRole?: 'admin';
  /** Only shown inside the native app. */
  nativeOnly?: boolean;
}

interface NavGroup {
  label: string;
  items: NavItem[];
  requiredRole?: 'admin';
}

const ICONS = {
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
  chat: 'M4 5h16v11H9l-5 4z',
  plug: 'M9 3v5M15 3v5M6 8h12v3a6 6 0 0 1-12 0zM12 17v4',
  gamepad: 'M6 12h4M8 10v4M15 11h.01M18 13h.01M7 6h10a5 5 0 0 1 5 5v2a4 4 0 0 1-7 2.6L14 14h-4l-1 1.6A4 4 0 0 1 2 13v-2a5 5 0 0 1 5-5z',
  news: 'M5 4h11v16H6a1 1 0 0 1-1-1zM16 8h3v11a1 1 0 0 1-1 1M8 8h5M8 12h5M8 16h3',
  pie: 'M12 3v9h9a9 9 0 1 1-9-9zM15 3.5A9 9 0 0 1 20.5 9H15z',
  spark: 'M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z',
  phone: 'M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM11 18h2',
  gear: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1.04 1.56V21a2 2 0 1 1-4 0v-.09A1.7 1.7 0 0 0 9 19.35a1.7 1.7 0 0 0-1.87.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.7 1.7 0 0 0 4.6 15a1.7 1.7 0 0 0-1.56-1.04H3a2 2 0 1 1 0-4h.09A1.7 1.7 0 0 0 4.65 9a1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1.04-1.56V3a2 2 0 1 1 4 0v.09A1.7 1.7 0 0 0 15 4.65a1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.7 1.7 0 0 0 19.4 9a1.7 1.7 0 0 0 1.56 1.04H21a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.51 1.04z',
} as const;

const SETTINGS_GROUP_LABEL = 'Settings';

const startsWith = (prefix: string) => (url: string) => url.startsWith(prefix);

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
    InstallPromptComponent,
    AndroidAppQrEntryComponent,
    UpdateRequiredComponent,
    UpdateAvailableBannerComponent,
  ],
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

  protected readonly groups: NavGroup[] = [
    {
      label: 'Game Catalog',
      items: [
        {
          label: 'Library',
          path: '/games/grid',
          icon: ICONS.grid,
          // Game detail lives at /games/:id, so anything under /games that is not another section counts.
          isActive: (url) =>
            url.startsWith('/games') &&
            !url.startsWith('/games/chat') &&
            !url.startsWith('/games/sources') &&
            !url.startsWith('/games/switch'),
        },
        {
          label: 'Chat',
          path: '/games/chat',
          icon: ICONS.chat,
          isActive: startsWith('/games/chat'),
        },
        {
          label: 'Sources',
          path: '/games/sources',
          icon: ICONS.plug,
          isActive: startsWith('/games/sources'),
          requiredRole: 'admin',
        },
        {
          label: 'Switch games',
          path: '/games/switch',
          icon: ICONS.gamepad,
          isActive: startsWith('/games/switch'),
          requiredRole: 'admin',
        },
      ],
    },
    {
      label: 'FNA · Finance',
      requiredRole: 'admin',
      items: [
        {
          label: 'Articles',
          path: '/fna/articles',
          icon: ICONS.news,
          isActive: startsWith('/fna/articles'),
        },
        {
          label: 'Portfolio',
          path: '/fna/portfolio',
          icon: ICONS.pie,
          isActive: startsWith('/fna/portfolio'),
        },
        {
          label: 'Briefing',
          path: '/fna/briefing',
          icon: ICONS.spark,
          isActive: startsWith('/fna/briefing'),
        },
      ],
    },
    {
      label: SETTINGS_GROUP_LABEL,
      items: [
        {
          label: 'Users',
          path: '/settings/users',
          icon: ICONS.gear,
          isActive: startsWith('/settings/users'),
          requiredRole: 'admin',
        },
        {
          label: 'AI Models',
          path: '/settings/ai-models',
          icon: ICONS.spark,
          isActive: startsWith('/settings/ai-models'),
          requiredRole: 'admin',
        },
        {
          label: 'App',
          path: '/settings/app',
          icon: ICONS.phone,
          isActive: startsWith('/settings/app'),
          nativeOnly: true,
        },
      ],
    },
  ];

  protected readonly visibleGroups = computed(() => {
    const admin = this.isAdmin();
    const native = this.platform.isNative();

    return this.groups
      .filter((group) => !group.requiredRole || admin)
      .map((group) => ({
        ...group,
        items: group.items.filter((item) => (!item.requiredRole || admin) && (!item.nativeOnly || native)),
      }))
      .filter((group) => group.items.length > 0);
  });

  protected readonly activePath = computed(() => {
    const url = this.#url();
    return (
      this.groups
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
