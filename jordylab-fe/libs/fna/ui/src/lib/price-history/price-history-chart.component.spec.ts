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

  it('points the drawing at the figures that describe it', () => {
    spectator = createComponent({
      props: {
        points: threePoints,
        label: 'Euro price',
        describedBy: 'price-history-figures',
      },
    });

    expect(spectator.query('svg')).toHaveAttribute(
      'aria-describedby',
      'price-history-figures',
    );
  });

  it('draws a soft area under the line down to the baseline', () => {
    spectator = createComponent({
      props: { points: threePoints, label: 'Euro price' },
    });

    const area = spectator.query('path');

    expect(area?.getAttribute('d')).toContain('Z');
    expect(area?.getAttribute('fill')).toMatch(/^url\(#price-area-\d+\)$/);
  });

  it('labels the price axis with round values across the period', () => {
    spectator = createComponent({
      props: { points: threePoints, label: 'Euro price' },
    });

    const text = spectator.element.textContent ?? '';

    expect(text).toContain('1,000');
    expect(text).toContain('1,300');
  });

  it('labels the date axis with the start, middle and end dates', () => {
    spectator = createComponent({
      props: { points: threePoints, label: 'Euro price' },
    });

    const text = spectator.element.textContent ?? '';

    expect(text).toContain('7 Nov 25');
    expect(text).toContain('2 Mar 26');
    expect(text).toContain('6 Nov 26');
  });

  describe('hovering', () => {
    /** The chart is 320 units wide and mapped to a 320 px box, so a client X equals a chart X. */
    const chartOf = (): SVGSVGElement => {
      const svg = spectator.query<SVGSVGElement>('svg');
      if (!svg) {
        throw new Error('the chart is not drawn');
      }
      vi.spyOn(svg, 'getBoundingClientRect').mockReturnValue({
        left: 0,
        width: 320,
      } as DOMRect);

      return svg;
    };

    const readoutOf = (): Element | null =>
      spectator.query('[data-testid="price-history-readout"]');

    it('shows the exact euro price and date of the point under the pointer', () => {
      spectator = createComponent({
        props: { points: threePoints, label: 'Euro price' },
      });
      const svg = chartOf();

      svg.dispatchEvent(new MouseEvent('pointermove', { clientX: 160 }));
      spectator.detectChanges();

      expect(readoutOf()?.textContent).toContain('€1,100.00');
      expect(readoutOf()?.textContent).toContain('2 Mar 2026');
    });

    it('snaps to the nearest recorded point when the pointer falls between two', () => {
      spectator = createComponent({
        props: { points: threePoints, label: 'Euro price' },
      });
      const svg = chartOf();

      svg.dispatchEvent(new MouseEvent('pointermove', { clientX: 240 }));
      spectator.detectChanges();

      expect(readoutOf()?.textContent).toContain('€1,250.00');
      expect(readoutOf()?.textContent).toContain('6 Nov 2026');
    });

    it('pins the readout to the left edge at the first point so it stays on the chart', () => {
      spectator = createComponent({
        props: { points: threePoints, label: 'Euro price' },
      });
      const svg = chartOf();

      svg.dispatchEvent(new MouseEvent('pointermove', { clientX: 0 }));
      spectator.detectChanges();

      expect(readoutOf()?.textContent).toContain('€1,000.00');
      expect(readoutOf()).toHaveStyle({ transform: 'translateX(0)' });
    });

    it('removes the readout when the pointer leaves the chart', () => {
      spectator = createComponent({
        props: { points: threePoints, label: 'Euro price' },
      });
      const svg = chartOf();
      svg.dispatchEvent(new MouseEvent('pointermove', { clientX: 160 }));
      spectator.detectChanges();

      svg.dispatchEvent(new MouseEvent('pointerleave'));
      spectator.detectChanges();

      expect(readoutOf()).toBeNull();
    });
  });
});
