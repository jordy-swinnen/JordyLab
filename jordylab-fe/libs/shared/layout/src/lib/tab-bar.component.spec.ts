import { provideRouter } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { TabBarComponent, TabBarItem } from './tab-bar.component';

const gamesTab: TabBarItem = {
  label: 'Games',
  icon: 'M3 3h7v7H3z',
  active: true,
  options: [
    { label: 'Library', icon: 'M3 3h7v7H3z', route: '/games/grid', active: true },
    { label: 'LibBot', icon: 'M10 3l1.6 4.7', route: '/games/libbot', active: false },
  ],
};
const settingsTab: TabBarItem = {
  label: 'Settings',
  icon: 'M12 15a3 3 0 1 0 0-6',
  active: false,
  options: [{ label: 'App', icon: 'M8 3h8', route: '/settings/app', active: false }],
};

describe('TabBarComponent', () => {
  const createComponent = createComponentFactory({ component: TabBarComponent, providers: [provideRouter([{ path: '**', children: [] }])] });
  let spectator: Spectator<TabBarComponent>;

  const optionLabels = () => spectator.queryAll('[data-testid="tab-bar-options"] a').map((link) => link.textContent?.trim());

  it('draws one tab per module', () => {
    spectator = createComponent({ props: { items: [gamesTab, settingsTab] } });

    expect(spectator.queryAll('nav > *').map((tab) => tab.textContent?.trim())).toEqual(['Games', 'Settings']);
  });

  it('keeps the pages of a module hidden until its tab is tapped', () => {
    spectator = createComponent({ props: { items: [gamesTab, settingsTab] } });

    expect(optionLabels()).toEqual([]);
  });

  it('shows the pages of a module when its tab is tapped', () => {
    spectator = createComponent({ props: { items: [gamesTab, settingsTab] } });

    spectator.click('nav button');

    expect(optionLabels()).toEqual(['Library', 'LibBot']);
    expect(spectator.query('nav button')?.getAttribute('aria-expanded')).toBe('true');
  });

  it('marks the page being viewed inside the sheet', () => {
    spectator = createComponent({ props: { items: [gamesTab, settingsTab] } });

    spectator.click('nav button');

    expect(spectator.queryAll('[data-testid="tab-bar-options"] a').map((link) => link.getAttribute('aria-current'))).toEqual(['page', null]);
  });

  it('closes the sheet when a page is chosen', () => {
    spectator = createComponent({ props: { items: [gamesTab, settingsTab] } });
    spectator.click('nav button');

    spectator.click('[data-testid="tab-bar-options"] a');

    expect(optionLabels()).toEqual([]);
  });

  it('closes the sheet when the tab is tapped again', () => {
    spectator = createComponent({ props: { items: [gamesTab, settingsTab] } });
    spectator.click('nav button');

    spectator.click('nav button');

    expect(optionLabels()).toEqual([]);
  });

  it('closes the sheet on the backdrop and on Escape', () => {
    spectator = createComponent({ props: { items: [gamesTab, settingsTab] } });
    spectator.click('nav button');
    spectator.click('button[aria-label="Close menu"]');
    expect(optionLabels()).toEqual([]);

    spectator.click('nav button');
    spectator.dispatchKeyboardEvent(document, 'keydown', 'Escape');

    expect(optionLabels()).toEqual([]);
  });

  it('moves focus into the sheet when it opens', async () => {
    spectator = createComponent({ props: { items: [gamesTab, settingsTab] } });

    spectator.click('nav button');
    await spectator.fixture.whenStable();

    expect(document.activeElement).toBe(spectator.query('[data-testid="tab-bar-options"] a'));
  });

  it('returns focus to the tab when the sheet is dismissed with Escape', async () => {
    spectator = createComponent({ props: { items: [gamesTab, settingsTab] } });
    spectator.click('nav button');
    await spectator.fixture.whenStable();

    spectator.dispatchKeyboardEvent(document, 'keydown', 'Escape');

    expect(document.activeElement).toBe(spectator.query('nav button'));
  });

  it('links a module with a single page straight to it', () => {
    spectator = createComponent({ props: { items: [gamesTab, settingsTab] } });
    const settings = spectator.queryAll('nav > *')[1];

    expect(settings.tagName).toBe('A');
    expect(settings.getAttribute('href')).toBe('/settings/app');
  });

  it('marks the module being viewed as the current page', () => {
    spectator = createComponent({ props: { items: [gamesTab, settingsTab] } });
    const [games, settings] = spectator.queryAll('nav > *');

    expect(games.getAttribute('aria-current')).toBe('page');
    expect(settings.getAttribute('aria-current')).toBeNull();
  });
});
