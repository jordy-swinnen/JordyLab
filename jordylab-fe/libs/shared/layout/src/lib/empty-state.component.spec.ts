import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { EmptyStateComponent } from './empty-state.component';

describe('EmptyStateComponent', () => {
  const createComponent = createComponentFactory(EmptyStateComponent);
  let spectator: Spectator<EmptyStateComponent>;

  beforeEach(() => {
    spectator = createComponent({ props: { title: 'No sources yet.', hint: 'Download the scan client.' } });
  });

  it('says what is missing and why', () => {
    expect(spectator.element).toHaveText('No sources yet.');
    expect(spectator.element).toHaveText('Download the scan client.');
  });

  it('shows no button without an action label', () => {
    expect(spectator.query('button')).toBeNull();
  });

  it('emits when the action is pressed', () => {
    const actionClicked = vi.fn();
    spectator.output('actionClicked').subscribe(actionClicked);
    spectator.setInput('actionLabel', 'Download scan client');

    spectator.click('button');

    expect(actionClicked).toHaveBeenCalledOnce();
  });
});
