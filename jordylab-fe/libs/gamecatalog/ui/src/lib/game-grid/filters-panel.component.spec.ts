import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aPlaceOptionMock, aPlatformChipMock } from '@jordylab-fe/gamecatalog/api';
import { FiltersPanelComponent } from './filters-panel.component';

describe('FiltersPanelComponent', () => {
  let spectator: Spectator<FiltersPanelComponent>;

  const createComponent = createComponentFactory(FiltersPanelComponent);

  beforeEach(() => {
    spectator = createComponent({
      props: {
        platforms: [aPlatformChipMock({ name: 'SNES' })],
        places: [aPlaceOptionMock({ label: 'Living room PC' })],
        selectedPlatforms: [],
        selectedPlaces: [],
        installStatus: 'INSTALLED',
        selectedSources: [],
        minLocalPlayers: null,
        selectedRomStatuses: [],
        selectedMarks: [],
        markScope: 'ALL',
        sort: 'TITLE',
        resultCount: 5,
        hasFilters: true,
      },
    });
  });

  it('leaves out the platform and where groups when the library has none', () => {
    spectator.setInput({ platforms: [], places: [] });

    expect(spectator.query('[data-testid="filter-group-platform"]')).toBeNull();
    expect(spectator.query('[data-testid="filter-group-where"]')).toBeNull();
  });

  it('shows a chip for every platform in the library, Nintendo Switch included', () => {
    spectator.setInput('platforms', [aPlatformChipMock({ name: 'SNES' }), aPlatformChipMock({ name: 'Nintendo Switch' })]);

    const chips = spectator.queryAll('[data-testid="filter-group-platform"] button').map((chip) => chip.textContent?.trim());
    expect(chips).toEqual(['SNES', 'Nintendo Switch']);
  });

  it('toggles the Nintendo Switch platform when its chip is pressed and shows it pressed once selected', () => {
    spectator.setInput('platforms', [aPlatformChipMock({ name: 'Nintendo Switch' })]);
    const toggled = vi.fn();
    spectator.output<string>('platformToggle').subscribe(toggled);

    spectator.click('[data-testid="filter-group-platform"] button');
    expect(toggled).toHaveBeenCalledWith('Nintendo Switch');

    spectator.setInput('selectedPlatforms', ['Nintendo Switch']);
    expect(spectator.query('[data-testid="filter-group-platform"] button')).toHaveAttribute('aria-pressed', 'true');
  });

  it('lets screen readers tell the groups apart', () => {
    const groups = spectator.queryAll('[role="group"]').map((group) => group.getAttribute('aria-label'));

    expect(groups).toEqual(
      expect.arrayContaining(['Platform', 'Where', 'Status', 'Source', 'Minimum local players', 'ROM status', 'Community marks', 'Whose marks', 'Sort']),
    );
  });

  it('shows "Any" until a number of players is chosen and then the minimum with a plus', () => {
    expect(spectator.query('output')).toHaveText('Any');

    spectator.setInput('minLocalPlayers', 6);

    expect(spectator.query('output')).toHaveText('6+');
  });

  it('disables Clear all when nothing is active', () => {
    spectator.setInput('hasFilters', false);

    const clear = spectator.queryAll<HTMLButtonElement>('button').find((button) => button.textContent?.trim() === 'Clear all');
    expect(clear?.disabled).toBe(true);
  });

  it('uses the singular when one game matches', () => {
    spectator.setInput('resultCount', 1);

    expect(spectator.query('[data-testid="filters-done"]')).toHaveText('Show 1 game');
  });
});
