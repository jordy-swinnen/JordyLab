/** Spec 014: an app the admin grants per person. Admins always have every app. */
export type GrantRole = 'app-gamecatalog';

export interface NavItem {
  label: string;
  path: string;
  icon: string;
  /** Extra route prefixes that should keep this item highlighted (e.g. game detail under Library). */
  isActive: (url: string) => boolean;
  /**
   * Omit for admin-or-guest; 'admin' hides the item from guests, 'guest' hides it from admins
   * (mirrors the backend access matrix).
   */
  requiredRole?: 'admin' | 'guest';
  /** Spec 014: only shown to admins or people granted this app. */
  requiredAppRole?: GrantRole;
}

export interface NavGroup {
  label: string;
  /** The module's name and icon on the phone's bottom bar. */
  tab: { label: string; icon: string };
  items: NavItem[];
  requiredRole?: 'admin' | 'guest';
  /** Spec 014: the whole module is hidden without this app grant. */
  requiredAppRole?: GrantRole;
}

export const ICONS = {
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
  sparkle: 'M10 3l1.6 4.7 4.7 1.6-4.7 1.6L10 15.6 8.4 10.9 3.7 9.3l4.7-1.6zM18 14l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z',
  plug: 'M9 3v5M15 3v5M6 8h12v3a6 6 0 0 1-12 0zM12 17v4',
  gamepad: 'M6 12h4M8 10v4M15 11h.01M18 13h.01M7 6h10a5 5 0 0 1 5 5v2a4 4 0 0 1-7 2.6L14 14h-4l-1 1.6A4 4 0 0 1 2 13v-2a5 5 0 0 1 5-5z',
  news: 'M5 4h11v16H6a1 1 0 0 1-1-1zM16 8h3v11a1 1 0 0 1-1 1M8 8h5M8 12h5M8 16h3',
  pie: 'M12 3v9h9a9 9 0 1 1-9-9zM15 3.5A9 9 0 0 1 20.5 9H15z',
  spark: 'M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z',
  phone: 'M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM11 18h2',
  gear: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1.04 1.56V21a2 2 0 1 1-4 0v-.09A1.7 1.7 0 0 0 9 19.35a1.7 1.7 0 0 0-1.87.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.7 1.7 0 0 0 4.6 15a1.7 1.7 0 0 0-1.56-1.04H3a2 2 0 1 1 0-4h.09A1.7 1.7 0 0 0 4.65 9a1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1.04-1.56V3a2 2 0 1 1 4 0v.09A1.7 1.7 0 0 0 15 4.65a1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.7 1.7 0 0 0 19.4 9a1.7 1.7 0 0 0 1.56 1.04H21a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.51 1.04z',
  download: 'M12 3v12m0 0l-4-4m4 4l4-4M4 19h16',
} as const;

const startsWith = (prefix: string) => (url: string) => url.startsWith(prefix);

export const SETTINGS_GROUP_LABEL = 'Settings';

export const NAVIGATION_GROUPS: NavGroup[] = [
  {
    label: 'Game Catalog',
    tab: { label: 'Games', icon: ICONS.gamepad },
    requiredAppRole: 'app-gamecatalog',
    items: [
      {
        label: 'Library',
        path: '/games/grid',
        icon: ICONS.grid,
        // Game detail lives at /games/:id, so anything under /games that is not another section counts.
        isActive: (url) =>
          url.startsWith('/games') &&
          !url.startsWith('/games/libbot') &&
          !url.startsWith('/games/sources') &&
          !url.startsWith('/games/consoles'),
      },
      {
        label: 'LibBot',
        path: '/games/libbot',
        icon: ICONS.sparkle,
        isActive: startsWith('/games/libbot'),
      },
      {
        label: 'Sources',
        path: '/games/sources',
        icon: ICONS.plug,
        isActive: startsWith('/games/sources'),
        requiredRole: 'admin',
      },
      {
        label: 'Consoles',
        path: '/games/consoles',
        icon: ICONS.gamepad,
        isActive: startsWith('/games/consoles'),
        requiredRole: 'admin',
      },
    ],
  },
  {
    label: 'FNA · Finance',
    tab: { label: 'Finance', icon: ICONS.news },
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
    tab: { label: 'Settings', icon: ICONS.gear },
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
        label: 'Machines',
        path: '/settings/machines',
        icon: ICONS.gear,
        isActive: startsWith('/settings/machines'),
        requiredRole: 'admin',
      },
      {
        // The desktop sync app's installers (spec 024 FR-008).
        label: 'Downloads',
        path: '/settings/downloads',
        icon: ICONS.download,
        isActive: startsWith('/settings/downloads'),
        requiredRole: 'admin',
      },
      {
        label: 'App',
        path: '/settings/app',
        icon: ICONS.phone,
        isActive: startsWith('/settings/app'),
      },
      {
        // One "Feedback" entry per persona: the guest submits and tracks their own tickets (spec 017 US1/US4)…
        label: 'Feedback',
        path: '/settings/feedback',
        icon: ICONS.sparkle,
        isActive: startsWith('/settings/feedback'),
        requiredRole: 'guest',
      },
      {
        // …the admin triages every ticket in the idea box (spec 017 US2/US3).
        label: 'Feedback',
        path: '/settings/inbox',
        icon: ICONS.sparkle,
        isActive: startsWith('/settings/inbox'),
        requiredRole: 'admin',
      },
    ],
  },
];

function isVisible(requiredRole: 'admin' | 'guest' | undefined, isAdmin: boolean, isGuest: boolean): boolean {
  switch (requiredRole) {
    case 'admin':
      return isAdmin;
    case 'guest':
      return isGuest;
    default:
      return isAdmin || isGuest;
  }
}

/** The groups and items this person may see. */
export function visibleNavigationGroups(isAdmin: boolean, isGuest: boolean, hasGameCatalog: boolean): NavGroup[] {
  const showsApp = (role?: GrantRole): boolean => !role || (role === 'app-gamecatalog' && hasGameCatalog);

  return NAVIGATION_GROUPS
    .filter((group) => isVisible(group.requiredRole, isAdmin, isGuest) && showsApp(group.requiredAppRole))
    .map((group) => ({
      ...group,
      items: group.items.filter(
        (item) => isVisible(item.requiredRole, isAdmin, isGuest) && showsApp(item.requiredAppRole),
      ),
    }))
    .filter((group) => group.items.length > 0);
}
