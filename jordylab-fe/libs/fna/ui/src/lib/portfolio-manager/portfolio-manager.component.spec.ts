import { computed, signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aPortfolioPositionMock, PortfolioPosition, PortfolioStore } from '@jordylab-fe/fna/api';
import { PortfolioManagerComponent } from './portfolio-manager.component';

describe('PortfolioManagerComponent', () => {
  const positions = signal<PortfolioPosition[]>([]);
  const error = signal<string | null>(null);
  const upsertPosition = vi.fn();
  const removePosition = vi.fn();

  const storeMock = {
    positions: positions.asReadonly(),
    error: error.asReadonly(),
    totalWorth: computed(() =>
      positions().reduce((sum, position) => {
        if (position.lastPrice !== null) {
          return sum + position.shareCount * position.lastPrice;
        }

        return sum;
      }, 0)
    ),
    hasAnyPrices: computed(() => positions().some((position) => position.lastPrice !== null)),
    load: vi.fn(),
    upsertPosition,
    removePosition,
  };

  const createComponent = createComponentFactory({
    component: PortfolioManagerComponent,
    providers: [{ provide: PortfolioStore, useValue: storeMock }],
  });

  let spectator: Spectator<PortfolioManagerComponent>;

  beforeEach(() => {
    positions.set([]);
    error.set(null);
    upsertPosition.mockClear();
    removePosition.mockClear();
    spectator = createComponent();
  });

  it('renders the portfolio manager view', () => {
    spectator.detectChanges();

    expect(spectator.query('lib-portfolio-manager-view')).toBeTruthy();
  });

  it('displays populated positions', () => {
    positions.set([
      aPortfolioPositionMock(),
      aPortfolioPositionMock({ id: 'p2', ticker: 'KBC', shareCount: 5, lastPrice: null, lastPriceFetchedAt: null }),
    ]);
    spectator.detectChanges();

    expect(spectator.query('td')).toHaveText('ABI');
    expect(spectator.query('p.text-2xl')).toHaveText('€705.00');
  });

  it('displays an empty state when no positions exist', () => {
    spectator.detectChanges();

    expect(spectator.query('h2')).toHaveText('Portfolio');
    expect(spectator.query('span.uppercase')).toHaveText('0 positions');
  });

  it('surfaces an error state when the store reports an error', () => {
    error.set('Failed to load portfolio.');
    spectator.detectChanges();

    expect(spectator.query('div.text-destructive')).toHaveText('Failed to load portfolio.');
  });

  it('adds a position via the store', () => {
    spectator.detectChanges();

    spectator.component.addPosition({ ticker: 'ABI', shares: 10 });

    expect(upsertPosition).toHaveBeenCalledWith('ABI', 10);
  });

  it('deletes a position via the store', () => {
    spectator.detectChanges();

    spectator.component.deletePosition('p1');

    expect(removePosition).toHaveBeenCalledWith('p1');
  });
});
