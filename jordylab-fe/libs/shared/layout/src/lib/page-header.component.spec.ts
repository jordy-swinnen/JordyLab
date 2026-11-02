import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { PageHeaderComponent } from './page-header.component';

describe('PageHeaderComponent', () => {
  const createComponent = createComponentFactory(PageHeaderComponent);
  let spectator: Spectator<PageHeaderComponent>;

  it('shows the title as the page heading', () => {
    spectator = createComponent({ props: { title: 'Library' } });

    expect(spectator.query('h2')).toHaveText('Library');
  });

  it('shows the subtitle only when one is given', () => {
    spectator = createComponent({ props: { title: 'Library' } });
    expect(spectator.query('p')).toBeNull();

    spectator.setInput('subtitle', '226 games');

    expect(spectator.query('p')).toHaveText('226 games');
  });
});
