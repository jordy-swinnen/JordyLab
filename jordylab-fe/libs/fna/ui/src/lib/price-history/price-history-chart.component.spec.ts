import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { PriceHistoryChartComponent } from './price-history-chart.component';

describe('PriceHistoryChartComponent', () => {
  const threePoints = [
    { date: '2025-11-07', priceInEuro: 1000 },
    { date: '2026-03-02', priceInEuro: 1100 },
    { date: '2026-11-06', priceInEuro: 1250 },
  ];

  const createComponent = createComponentFactory({
    component: PriceHistoryChartComponent,
  });

  let spectator: Spectator<PriceHistoryChartComponent>;

  it('draws one line through every recorded point', () => {
    spectator = createComponent({
      props: { points: threePoints, label: 'Euro price' },
    });

    const coordinates =
      spectator.query('polyline')?.getAttribute('points')?.split(' ') ?? [];

    expect(coordinates).toHaveLength(3);
  });

  it('marks the end of the line with a dot', () => {
    spectator = createComponent({
      props: { points: threePoints, label: 'Euro price' },
    });

    expect(spectator.query('circle')).toBeTruthy();
  });

  it('draws nothing with fewer than two points', () => {
    spectator = createComponent({
      props: { points: [threePoints[0]], label: 'Euro price' },
    });

    expect(spectator.query('svg')).toBeNull();
    expect(spectator.query('polyline')).toBeNull();
  });

  it('gives the drawing an accessible label', () => {
    spectator = createComponent({
      props: {
        points: threePoints,
        label: 'Euro price from 7 Nov 2025 to 6 Nov 2026',
      },
    });

    expect(spectator.query('svg')).toHaveAttribute('role', 'img');
    expect(spectator.query('svg')).toHaveAttribute(
      'aria-label',
      'Euro price from 7 Nov 2025 to 6 Nov 2026',
    );
  });
});
