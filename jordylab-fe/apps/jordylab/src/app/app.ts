import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  NavigationEnd,
  Router,
  RouterLink,
  RouterOutlet,
} from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '@jordylab-fe/shared/auth';
import {
  BrandMarkComponent,
  WordmarkComponent,
} from '@jordylab-fe/shared/brand';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  /** Extra route prefixes that should keep this item highlighted (e.g. game detail under Library). */
  isActive: (url: string) => boolean;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const ICONS = {
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
  chat: 'M4 5h16v11H9l-5 4z',
  plug: 'M9 3v5M15 3v5M6 8h12v3a6 6 0 0 1-12 0zM12 17v4',
  news: 'M5 4h11v16H6a1 1 0 0 1-1-1zM16 8h3v11a1 1 0 0 1-1 1M8 8h5M8 12h5M8 16h3',
  pie: 'M12 3v9h9a9 9 0 1 1-9-9zM15 3.5A9 9 0 0 1 20.5 9H15z',
  spark: 'M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z',
} as const;

const startsWith = (prefix: string) => (url: string) => url.startsWith(prefix);

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, BrandMarkComponent, WordmarkComponent],
  templateUrl: './app.html',
})
export class App {
  #auth = inject(AuthService);
  #router = inject(Router);

  username = this.#auth.username;

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
            !url.startsWith('/games/sources'),
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
        },
      ],
    },
    {
      label: 'FNA · Finance',
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
  ];

  protected readonly activePath = computed(() => {
    const url = this.#url();
    return (
      this.groups
        .flatMap((group) => group.items)
        .find((item) => item.isActive(url))?.path ?? null
    );
  });

  protected readonly initial = computed(() =>
    (this.username() ?? '?').charAt(0).toUpperCase(),
  );

  async onLogout(): Promise<void> {
    await this.#auth.logout();
  }
}
