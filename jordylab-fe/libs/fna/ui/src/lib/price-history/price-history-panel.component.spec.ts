import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aPriceHistoryMock, PriceHistoryPeriod } from '@jordylab-fe/fna/api';
import { PriceHistoryPanelComponent } from './price-history-panel.component';

describe('PriceHistoryPanelComponent', () => {
  const createComponent = createComponentFactory({
    component: PriceHistoryPanelComponent,
  });

  let spectator: Spectator<PriceHistoryPanelComponent>;

  beforeEach(() => {
    spectator = createComponent({ props: { ticker: 'ABI' } });
  });

  it('shows the ticker as the heading of the panel', () => {
    spectator.setInput({ history: aPriceHistoryMock() });

    expect(spectator.query('h3')).toHaveText('ABI');
  });

  it('marks the chosen period as pressed and emits the period the investor picks', () => {
    const periodSelected = vi.fn<(period: PriceHistoryPeriod) => void>();
    spectator.output('periodSelected').subscribe(periodSelected);
    spectator.setInput({
      history: aPriceHistoryMock(),
      selectedPeriod: 'ONE_YEAR',
    });

    expect(spectator.query('button[aria-pressed="true"]')).toHaveText('1Y');

    spectator.click(findButton('3M'));

    expect(periodSelected).toHaveBeenCalledWith('THREE_MONTHS');
  });

  it('names the start, the end and the change in the chart label', () => {
    spectator.setInput({ history: aPriceHistoryMock() });

    expect(spectator.query('svg')?.getAttribute('aria-label')).toContain(
      'up 250.00 euro or 25.00 percent',
    );
  });

  it('links the chart to the figures so a screen reader reads them with it', () => {
    spectator.setInput({ history: aPriceHistoryMock() });

    const figuresId = spectator.query('dl')?.id;

    expect(figuresId).toBeTruthy();
    expect(spectator.query('svg')).toHaveAttribute(
      'aria-describedby',
      figuresId,
    );
  });

  it('shows the figures as text, with the change signed', () => {
    spectator.setInput({ history: aPriceHistoryMock() });

    const figures = spectator.query('dl');

    expect(figures).toHaveText('€1,000.00');
    expect(figures).toHaveText('€1,250.00');
    expect(figures).toHaveText('+€250.00');
    expect(figures).toHaveText('+25.00%');
  });

  it('says that no price history is recorded yet, without offering a button', () => {
    spectator.setInput({
      history: aPriceHistoryMock({
        status: 'NO_HISTORY_YET',
        points: [],
        summary: null,
      }),
    });

    expect(spectator.element).toHaveText('No price history yet.');
    expect(spectator.query('dl')).toBeNull();
  });

  it('explains that a change cannot be calculated from a single price', () => {
    spectator.setInput({
      history: aPriceHistoryMock({
        points: [{ date: '2026-11-06', priceInEuro: 1250 }],
        summary: {
          startDate: '2026-11-06',
          startPriceInEuro: 1250,
          endDate: '2026-11-06',
          endPriceInEuro: 1250,
          changeInEuro: null,
          changePercent: null,
          highestPriceInEuro: 1250,
          lowestPriceInEuro: 1250,
        },
      }),
    });

    expect(spectator.query('dl')).toHaveText(
      'Not enough history yet to show a change.',
    );
  });

  it('says when the history starts after the period begins', () => {
    spectator.setInput({
      history: aPriceHistoryMock({
        windowStartDate: '2025-11-07',
        dataStartDate: '2026-01-05',
      }),
    });

    expect(spectator.element).toHaveText('History starts on 5 Jan 2026.');
  });

  it('shows a failed load in plain words and offers to try again', () => {
    const retried = vi.fn();
    spectator.output('retried').subscribe(retried);
    spectator.setInput({
      error: "Couldn't load the price history. Try again in a moment.",
    });

    expect(spectator.query('[role="alert"]')).toHaveText(
      "Couldn't load the price history. Try again in a moment.",
    );

    spectator.click(findButton('Try again'));

    expect(retried).toHaveBeenCalledOnce();
  });

  it('holds its place while loading, with no figures yet', () => {
    spectator.setInput({ history: null, isLoading: true });

    expect(spectator.query('[aria-busy="true"]')).toBeTruthy();
    expect(spectator.query('dl')).toBeNull();
  });

  it('closes when the Close button is pressed', () => {
    const closed = vi.fn();
    spectator.output('closed').subscribe(closed);
    spectator.setInput({ history: aPriceHistoryMock() });

    spectator.click(findButton('Close'));

    expect(closed).toHaveBeenCalledOnce();
  });

  function findButton(label: string): HTMLButtonElement {
    const button = spectator
      .queryAll('button')
      .find((candidate) => candidate.textContent?.trim() === label);

    if (!button) {
      throw new Error(`No button labelled ${label}`);
    }

    return button as HTMLButtonElement;
  }
});
