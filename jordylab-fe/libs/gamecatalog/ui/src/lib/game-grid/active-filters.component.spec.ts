import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { ActiveFilter } from '@jordylab-fe/gamecatalog/api';
import { ActiveFiltersComponent } from './active-filters.component';

describe('ActiveFiltersComponent', () => {
  let spectator: Spectator<ActiveFiltersComponent>;
  const platform: ActiveFilter = { id: 'platform:SNES', key: 'Platform', value: 'SNES', remove: vi.fn() };
  const status: ActiveFilter = { id: 'status', key: 'Status', value: 'Installed', remove: vi.fn() };

  const createComponent = createComponentFactory(ActiveFiltersComponent);

  beforeEach(() => {
    vi.mocked(platform.remove).mockReset();
    vi.mocked(status.remove).mockReset();
  });

  it('gives every chip a remove button that names the filter', () => {
    spectator = createComponent({ props: { filters: [platform, status], totalElements: 12 } });

    expect(spectator.queryAll('button[aria-label^="Remove filter"]').map((button) => button.getAttribute('aria-label'))).toEqual([
      'Remove filter Platform: SNES',
      'Remove filter Status: Installed',
    ]);
  });

  it('removes just the clicked filter', () => {
    spectator = createComponent({ props: { filters: [platform, status], totalElements: 12 } });

    spectator.click('[aria-label="Remove filter Status: Installed"]');

    expect(status.remove).toHaveBeenCalled();
    expect(platform.remove).not.toHaveBeenCalled();
  });

  it('offers Clear all only when more than one filter is in use', () => {
    spectator = createComponent({ props: { filters: [status], totalElements: 12 } });
    expect(spectator.query('[data-testid="clear-all-filters"]')).toBeNull();

    spectator.setInput('filters', [platform, status]);

    expect(spectator.query('[data-testid="clear-all-filters"]')).toBeTruthy();
  });

  it('emits clearAll', () => {
    spectator = createComponent({ props: { filters: [platform, status], totalElements: 12 } });
    const cleared = vi.fn();
    spectator.output('clearAll').subscribe(cleared);

    spectator.click('[data-testid="clear-all-filters"]');

    expect(cleared).toHaveBeenCalled();
  });

  it('announces the count politely and uses the singular for one game', () => {
    spectator = createComponent({ props: { filters: [status], totalElements: 1 } });

    const count = spectator.query('[data-testid="result-count"]');
    expect(count?.getAttribute('aria-live')).toBe('polite');
    expect(count).toHaveText('1 game');
    expect(count).not.toHaveText('1 games');
  });
});
