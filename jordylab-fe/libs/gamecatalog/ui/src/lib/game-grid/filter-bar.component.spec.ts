import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { FilterBarComponent } from './filter-bar.component';

describe('FilterBarComponent', () => {
  let spectator: Spectator<FilterBarComponent>;

  const createComponent = createComponentFactory(FilterBarComponent);

  beforeEach(() => {
    spectator = createComponent({ props: { searchTerm: '', activeCount: 0, sort: 'TITLE' } });
  });

  it('shows no count on the Filters button while nothing is active', () => {
    expect(spectator.query('[data-testid="filters-count"]')).toBeNull();
  });

  it('shows how many filters are active and says so to a screen reader', () => {
    spectator.setInput('activeCount', 3);

    const badge = spectator.query('[data-testid="filters-count"]');
    expect(badge).toHaveText('3');
    expect(badge?.getAttribute('aria-label')).toBe('3 filters active');
  });

  it('opens and closes from the Filters button and keeps aria-expanded in step', () => {
    spectator.click('[data-testid="filters-button"]');
    expect(spectator.component.open()).toBe(true);
    expect(spectator.query('[data-testid="filters-button"]')?.getAttribute('aria-expanded')).toBe('true');

    spectator.click('[data-testid="filters-button"]');

    expect(spectator.component.open()).toBe(false);
  });

  it('closes on Escape and returns focus to the button', () => {
    spectator.click('[data-testid="filters-button"]');

    spectator.dispatchKeyboardEvent('[data-testid="filter-bar"]', 'keydown', 'Escape');

    expect(spectator.component.open()).toBe(false);
    expect(document.activeElement).toBe(spectator.query('[data-testid="filters-button"]'));
  });

  it('emits the typed search and the chosen sort', () => {
    const searched = vi.fn();
    const sorted = vi.fn();
    spectator.output('searchChange').subscribe(searched);
    spectator.output('sortChange').subscribe(sorted);

    spectator.typeInElement('zelda', 'input[type="search"]');
    spectator.selectOption('[data-testid="sort-select"]', 'MOST_WANTED');

    expect(searched).toHaveBeenCalledWith('zelda');
    expect(sorted).toHaveBeenCalledWith('MOST_WANTED');
  });
});
