import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aPriceHistoryMock, aWishlistItemMock } from '@jordylab-fe/fna/api';
import { WishlistTableComponent } from './wishlist-table.component';

describe('WishlistTableComponent', () => {
  let spectator: Spectator<WishlistTableComponent>;
  const createComponent = createComponentFactory({
    component: WishlistTableComponent,
    detectChanges: false,
  });

  const items = [
    aWishlistItemMock({ id: 'w1', ticker: 'ASML', lastPrice: 642.8 }),
    aWishlistItemMock({ id: 'w2', ticker: 'NEW', lastPrice: null }),
  ];

  function historyButton(itemId: string): HTMLButtonElement | null {
    return spectator.query<HTMLButtonElement>(
      `[data-history-button="${itemId}"]`,
    );
  }

  function buttonNamed(label: string): HTMLButtonElement {
    return Array.from(spectator.queryAll<HTMLButtonElement>('button')).find(
      (button) => button.textContent?.trim() === label,
    ) as HTMLButtonElement;
  }

  beforeEach(() => {
    spectator = createComponent({ props: { wishlist: items } });
    spectator.detectChanges();
  });

  it('renders a row per wishlist item with its price, or a dash when unpriced', () => {
    const rows = spectator.queryAll('[data-testid="wishlist-row"]');

    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveText('€642.80');
    expect(rows[1]).toHaveText('—');
  });

  it('asks to open the history of the clicked item', () => {
    const opened = vi.fn();
    spectator.output<string>('openHistory').subscribe(opened);

    spectator.click(historyButton('w1') as HTMLButtonElement);

    expect(opened).toHaveBeenCalledWith('w1');
  });

  it('asks to remove the item whose Remove button was clicked', () => {
    const removed = vi.fn();
    spectator.output<string>('deleteWishlistItem').subscribe(removed);

    spectator.click(buttonNamed('Remove'));

    expect(removed).toHaveBeenCalledWith('w1');
  });

  describe('promoting an item to a holding', () => {
    let promoted: ReturnType<typeof vi.fn>;

    beforeEach(() => {
      promoted = vi.fn();
      spectator.output('addPosition').subscribe(promoted);
      spectator.click(buttonNamed('Promote'));
    });

    it('shows the shares field in place of the row actions', () => {
      expect(spectator.query('input[aria-label="Promote shares"]')).toExist();
      expect(historyButton('w1')).toBeNull();
    });

    it('promotes with the entered share count and closes the field', () => {
      spectator.typeInElement('2.5', 'input[aria-label="Promote shares"]');
      spectator.click(buttonNamed('Confirm'));

      expect(promoted).toHaveBeenCalledWith({ ticker: 'ASML', shares: 2.5 });
      expect(spectator.query('input[aria-label="Promote shares"]')).toBeNull();
    });

    it('does not promote without a share count', () => {
      spectator.click(buttonNamed('Confirm'));

      expect(promoted).not.toHaveBeenCalled();
    });

    it('does not promote with a share count of zero', () => {
      spectator.typeInElement('0', 'input[aria-label="Promote shares"]');
      spectator.click(buttonNamed('Confirm'));

      expect(promoted).not.toHaveBeenCalled();
    });

    it('cancels without promoting and brings the row actions back', () => {
      spectator.typeInElement('3', 'input[aria-label="Promote shares"]');
      spectator.click(buttonNamed('Cancel'));

      expect(promoted).not.toHaveBeenCalled();
      expect(historyButton('w1')).not.toBeNull();
    });
  });

  describe('with a history open', () => {
    beforeEach(() => {
      spectator.setInput({
        openedPositionId: 'w1',
        history: aPriceHistoryMock({ positionId: 'w1', ticker: 'ASML' }),
      });
    });

    it('replaces the opened item row with the history panel', () => {
      expect(spectator.query('[data-testid="price-history-panel"]')).toHaveText(
        'ASML',
      );
      expect(historyButton('w1')).toBeNull();
      expect(historyButton('w2')).not.toBeNull();
    });

    it('passes a period choice on', () => {
      const chosen = vi.fn();
      spectator.output('historyPeriodSelected').subscribe(chosen);

      spectator.click(buttonNamed('1M'));

      expect(chosen).toHaveBeenCalledWith('ONE_MONTH');
    });

    it('asks to close, and returns focus to the History button once the row is back', async () => {
      const closed = vi.fn();
      spectator.output('closeHistory').subscribe(() => {
        closed();
        spectator.fixture.componentRef.setInput('openedPositionId', null);
      });

      spectator.click(buttonNamed('Close'));
      await spectator.fixture.whenStable();

      expect(closed).toHaveBeenCalledOnce();
      expect(document.activeElement).toBe(historyButton('w1'));
    });
  });
});
