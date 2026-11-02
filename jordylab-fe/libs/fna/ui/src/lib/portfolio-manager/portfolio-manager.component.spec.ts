import { computed, signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aPortfolioPositionMock, aWishlistItemMock, PortfolioPosition, WishlistItem, PortfolioStore } from '@jordylab-fe/fna/api';
import { PortfolioManagerComponent } from './portfolio-manager.component';

describe('PortfolioManagerComponent', () => {
  const positions = signal<PortfolioPosition[]>([]);
  const wishlist = signal<WishlistItem[]>([]);
  const error = signal<string | null>(null);
  const wishlistError = signal<string | null>(null);
  const upsertPosition = vi.fn();
  const removePosition = vi.fn();
  const addWishlistItem = vi.fn();
  const removeWishlistItem = vi.fn();

  const storeMock = {
    positions: positions.asReadonly(),
    wishlist: wishlist.asReadonly(),
    positionRows: computed(() =>
      positions().map((position) => ({
        ...position,
        value: position.lastPrice === null ? null : position.shareCount * position.lastPrice,
      }))
    ),
    error: error.asReadonly(),
    wishlistError: wishlistError.asReadonly(),
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
    addWishlistItem,
    removeWishlistItem,
  };

  const createComponent = createComponentFactory({
    component: PortfolioManagerComponent,
    providers: [{ provide: PortfolioStore, useValue: storeMock }],
  });

  let spectator: Spectator<PortfolioManagerComponent>;

  beforeEach(() => {
    positions.set([]);
    wishlist.set([]);
    error.set(null);
    wishlistError.set(null);
    upsertPosition.mockClear();
    removePosition.mockClear();
    addWishlistItem.mockClear();
    removeWishlistItem.mockClear();
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
    expect(spectator.element).toHaveText('0 positions');
    expect(spectator.element).toHaveText('No holdings yet.');
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

  it('shows the wishlist empty state when nothing is on the wishlist', () => {
    spectator.detectChanges();

    expect(spectator.element).toHaveText('No wishlist stocks yet.');
  });

  it('renders a wishlist row per wishlist item', () => {
    wishlist.set([aWishlistItemMock(), aWishlistItemMock({ id: 'w2', ticker: 'SAP' })]);
    spectator.detectChanges();

    const rows = spectator.queryAll('[data-testid="wishlist-row"]');

    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveText('ASML');
    expect(rows[1]).toHaveText('SAP');
  });

  it('shows the wishlist price and a dash when the wishlist item is unpriced', () => {
    wishlist.set([
      aWishlistItemMock({ id: 'w1', ticker: 'ASML', lastPrice: 642.8 }),
      aWishlistItemMock({ id: 'w2', ticker: 'SAP', lastPrice: null, lastPriceFetchedAt: null }),
    ]);
    spectator.detectChanges();

    const rows = spectator.queryAll('[data-testid="wishlist-row"]');

    expect(rows[0]).toHaveText('€642.80');
    expect(rows[1]).toHaveText('—');
  });

  it('keeps wishlist items out of total worth, allocation and the position count', () => {
    positions.set([aPortfolioPositionMock({ shareCount: 10, lastPrice: 70.5 })]);
    wishlist.set([aWishlistItemMock({ lastPrice: 642.8 })]);
    spectator.detectChanges();

    expect(spectator.query('[data-testid="total-worth"]')).toHaveText('€705.00');
    expect(spectator.element).toHaveText('1 position');
    expect(spectator.queryAll('ul li')).toHaveLength(1);
  });

  it('shows the wishlist conflict inline without hiding the portfolio', () => {
    positions.set([aPortfolioPositionMock()]);
    wishlistError.set('This ticker is already in your portfolio.');
    spectator.detectChanges();

    expect(spectator.query('[data-testid="wishlist-error"]')).toHaveText(
      'This ticker is already in your portfolio.'
    );
    expect(spectator.query('[data-testid="total-worth"]')).toHaveText('€705.00');
    expect(spectator.query('[data-testid="wishlist-row"]')).toBeNull();
  });

  it('adds a wishlist ticker via the store', () => {
    spectator.detectChanges();

    spectator.component.addWishlistItem('ASML');

    expect(addWishlistItem).toHaveBeenCalledWith('ASML');
  });

  it('deletes a wishlist item via the store', () => {
    spectator.detectChanges();

    spectator.component.deleteWishlistItem('w1');

    expect(removeWishlistItem).toHaveBeenCalledWith('w1');
  });

  it('promotes a wishlist item with the entered share count', () => {
    wishlist.set([aWishlistItemMock({ id: 'w1', ticker: 'ASML' })]);
    spectator.detectChanges();

    clickButton('Promote');
    spectator.typeInElement('2', 'input[aria-label="Promote shares"]');
    clickButton('Confirm');

    expect(upsertPosition).toHaveBeenCalledWith('ASML', 2);
  });

  it('does not promote with a missing or non-positive share count', () => {
    wishlist.set([aWishlistItemMock({ id: 'w1', ticker: 'ASML' })]);
    spectator.detectChanges();

    clickButton('Promote');
    spectator.typeInElement('0', 'input[aria-label="Promote shares"]');
    clickButton('Confirm');

    expect(upsertPosition).not.toHaveBeenCalled();
  });

  function clickButton(label: string): void {
    const button = spectator
      .queryAll('button')
      .find((candidate) => candidate.textContent?.trim() === label);

    if (!button) {
      throw new Error(`No button labelled ${label}`);
    }

    spectator.click(button);
  }
  it('loads the portfolio again when the error state is retried', () => {
    storeMock.load.mockClear();
    error.set('Failed to load portfolio.');
    spectator.detectChanges();

    spectator.click('lib-error-state button');

    expect(storeMock.load).toHaveBeenCalledOnce();
  });
});
