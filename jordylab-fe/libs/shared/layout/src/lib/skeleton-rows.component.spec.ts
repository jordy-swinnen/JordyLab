import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { SkeletonRowsComponent } from './skeleton-rows.component';

describe('SkeletonRowsComponent', () => {
  const createComponent = createComponentFactory(SkeletonRowsComponent);
  let spectator: Spectator<SkeletonRowsComponent>;

  it('draws four rows of 84 pixels by default', () => {
    spectator = createComponent();
    const rows = spectator.queryAll<HTMLElement>('hlm-skeleton');

    expect(rows).toHaveLength(4);
    expect(rows[0].style.height).toBe('84px');
  });

  it('follows the requested count and height', () => {
    spectator = createComponent({ props: { count: 2, height: 120 } });
    const rows = spectator.queryAll<HTMLElement>('hlm-skeleton');

    expect(rows).toHaveLength(2);
    expect(rows[1].style.height).toBe('120px');
  });

  it('is hidden from screen readers', () => {
    spectator = createComponent();

    expect(spectator.query('[aria-hidden="true"]')).not.toBeNull();
  });
});
