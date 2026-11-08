import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import {
  aPortfolioPositionMock,
  aPriceHistoryMock,
  PortfolioPositionRow,
} from '@jordylab-fe/fna/api';
import { HoldingsTableComponent } from './holdings-table.component';

function aRow(overrides: Partial<PortfolioPositionRow> = {}) {
  const position = aPortfolioPositionMock(overrides);
  return {
    ...position,
    value:
      position.lastPrice === null
        ? null
        : position.shareCount * position.lastPrice,
    ...overrides,
  };
}

describe('HoldingsTableComponent', () => {
  let spectator: Spectator<HoldingsTableComponent>;
  const createComponent = createComponentFactory({
    component: HoldingsTableComponent,
    detectChanges: false,
  });

  const rows = [
    aRow({ id: 'p1', ticker: 'ABI', shareCount: 10, lastPrice: 70.5 }),
    aRow({ id: 'p2', ticker: 'ASML', shareCount: 2, lastPrice: 642.8 }),
  ];

  function historyButton(positionId: string): HTMLButtonElement | null {
    return spectator.query<HTMLButtonElement>(
      `[data-history-button="${positionId}"]`,
    );
  }

  beforeEach(() => {
    spectator = createComponent({ props: { positions: rows } });
    spectator.detectChanges();
  });

  it('renders a row per holding with its price and value', () => {
    expect(spectator.queryAll('tbody tr')).toHaveLength(2);
    expect(spectator.queryAll('[data-testid="row-price"]')[0]).toHaveText(
      '€70.50',
    );
    expect(spectator.queryAll('[data-testid="row-value"]')[0]).toHaveText(
      '€705.00',
    );
  });

  it('shows a dash instead of a price and value for an unpriced holding', () => {
    spectator.setInput('positions', [
      aRow({ id: 'p3', ticker: 'NEW', lastPrice: null }),
    ]);

    expect(spectator.query('[data-testid="row-price"]')).toBeNull();
    expect(spectator.query('[data-testid="row-value"]')).toBeNull();
    expect(spectator.query('tbody tr')).toHaveText('—');
  });

  it('names each History button after its holding', () => {
    expect(historyButton('p2')).toHaveAttribute(
      'aria-label',
      'History for ASML',
    );
  });

  it('asks to open the history of the clicked holding', () => {
    const opened = vi.fn();
    spectator.output<string>('openHistory').subscribe(opened);

    spectator.click(historyButton('p2') as HTMLButtonElement);

    expect(opened).toHaveBeenCalledWith('p2');
  });

  it('asks to remove the holding whose Remove button was clicked', () => {
    const removed = vi.fn();
    spectator.output<string>('deletePosition').subscribe(removed);

    spectator.click(
      spectator.queryAll('tbody tr')[1].querySelectorAll('button')[1],
    );

    expect(removed).toHaveBeenCalledWith('p2');
  });

  describe('with a history open', () => {
    beforeEach(() => {
      spectator.setInput({
        openedPositionId: 'p1',
        history: aPriceHistoryMock({ positionId: 'p1', ticker: 'ABI' }),
      });
    });

    it('replaces the opened holding row with the history panel', () => {
      expect(spectator.query('[data-testid="price-history-panel"]')).toHaveText(
        'ABI',
      );
      expect(historyButton('p1')).toBeNull();
    });

    it('keeps the other holdings as normal rows', () => {
      expect(historyButton('p2')).not.toBeNull();
    });

    it('passes a period choice on', () => {
      const chosen = vi.fn();
      spectator.output('historyPeriodSelected').subscribe(chosen);

      spectator.click(
        Array.from(spectator.queryAll<HTMLButtonElement>('button')).find(
          (button) => button.textContent?.trim() === '3M',
        ) as HTMLButtonElement,
      );

      expect(chosen).toHaveBeenCalledWith('THREE_MONTHS');
    });

    it('asks to close, and returns focus to the History button once the row is back', async () => {
      const closed = vi.fn();
      spectator.output('closeHistory').subscribe(() => {
        closed();
        spectator.fixture.componentRef.setInput('openedPositionId', null);
      });

      spectator.click(
        Array.from(spectator.queryAll<HTMLButtonElement>('button')).find(
          (button) => button.textContent?.trim() === 'Close',
        ) as HTMLButtonElement,
      );
      await spectator.fixture.whenStable();

      expect(closed).toHaveBeenCalledOnce();
      expect(document.activeElement).toBe(historyButton('p1'));
    });
  });
});
