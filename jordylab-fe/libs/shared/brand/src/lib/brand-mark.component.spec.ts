import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { BrandMarkComponent } from './brand-mark.component';
import { WordmarkComponent } from './wordmark.component';

describe('BrandMarkComponent', () => {
  const createComponent = createComponentFactory(BrandMarkComponent);
  let spectator: Spectator<BrandMarkComponent>;

  it('renders a square svg at the requested size', () => {
    spectator = createComponent({ props: { size: 48 } });
    const svg = spectator.query('svg') as SVGElement;

    expect(svg.getAttribute('width')).toBe('48');
    expect(svg.getAttribute('height')).toBe('48');
  });
});

describe('WordmarkComponent', () => {
  const createComponent = createComponentFactory(WordmarkComponent);

  it('spells the product name', () => {
    const spectator = createComponent();

    expect(spectator.element).toHaveText('JordyLab');
  });
});
