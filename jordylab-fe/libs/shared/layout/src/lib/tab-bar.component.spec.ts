import { provideRouter } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { TabBarComponent, TabBarItem } from './tab-bar.component';

const libraryItem: TabBarItem = { label: 'Library', icon: 'M3 3h7v7H3z', route: '/games/grid', active: true };
const botItem: TabBarItem = { label: 'LibBot', icon: 'M10 3l1.6 4.7', route: '/games/libbot', active: false };

describe('TabBarComponent', () => {
  const createComponent = createComponentFactory({ component: TabBarComponent, providers: [provideRouter([])] });
  let spectator: Spectator<TabBarComponent>;

  it('draws one link per item it is given', () => {
    spectator = createComponent({ props: { items: [libraryItem, botItem] } });

    expect(spectator.queryAll('a')).toHaveLength(2);
  });

  it('draws three items for a person without the finance tab', () => {
    spectator = createComponent({ props: { items: [libraryItem, botItem, { ...botItem, label: 'More', route: '/more' }] } });

    expect(spectator.queryAll('a')).toHaveLength(3);
  });

  it('marks only the active item as the current page', () => {
    spectator = createComponent({ props: { items: [libraryItem, botItem] } });
    const [library, bot] = spectator.queryAll('a');

    expect(library.getAttribute('aria-current')).toBe('page');
    expect(bot.getAttribute('aria-current')).toBeNull();
  });
});
