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
    positionRows: computed(() =>
      positions().map((position) => ({
        ...position,
        value: position.lastPrice === null ? null : position.shareCount * position.lastPrice,
      }))
    ),
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
    expect(spectator.query('[data-testid="total-worth"]')).toHaveText('€705.00');
  });

  it('shows each position value as shares times last price, in the total-worth colour', () => {
    positions.set([aPortfolioPositionMock({ shareCount: 0.137, lastPrice: 73805.21 })]);
    spectator.detectChanges();

    const value = spectator.query('[data-testid="row-value"]');

    expect(value).toHaveText('€10,111.31');
    expect(spectator.query('[data-testid="row-price"]')).toHaveText('€73,805.21');
  });

  it('shows a dash for the value of an unpriced position', () => {
    positions.set([aPortfolioPositionMock({ lastPrice: null, lastPriceFetchedAt: null })]);
    spectator.detectChanges();

    expect(spectator.query('[data-testid="row-value"]')).toBeNull();
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

  it('draws an allocation bar with a slice per priced position', () => {
    positions.set([
      aPortfolioPositionMock({ id: 'p1', ticker: 'ABI' }),
      aPortfolioPositionMock({ id: 'p2', ticker: 'KBC', shareCount: 5, lastPrice: null, lastPriceFetchedAt: null }),
    ]);
    spectator.detectChanges();

    const legend = spectator.queryAll('ul li');

    expect(legend).toHaveLength(1);
    expect(legend[0]).toHaveText('ABI');
    expect(legend[0]).toHaveText('100%');
  });

  it('shows every digit of a small crypto position and values it with them', () => {
    positions.set([aPortfolioPositionMock({ ticker: 'BTC-EUR', shareCount: 0.002106, lastPrice: 76318.08 })]);
    spectator.detectChanges();

    expect(spectator.query('tbody')?.textContent).toContain('0.002106');
    expect(spectator.query('[data-testid="row-value"]')?.textContent?.trim()).toBe('€160.73');
  });

  it('lets the shares field take fractions', () => {
    spectator.detectChanges();

    expect(spectator.query('input[aria-label="Shares"]')?.getAttribute('step')).toBe('any');
  });
});
