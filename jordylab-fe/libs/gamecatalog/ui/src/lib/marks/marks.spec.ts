import { createComponentFactory } from '@ngneat/spectator/vitest';
import { MarkButtonsComponent } from './mark-buttons.component';
import { VoteRailComponent } from './vote-rail.component';

describe('MarkButtonsComponent', () => {
  const createComponent = createComponentFactory(MarkButtonsComponent);

  it('has three buttons at least 44 px high, named by their mark', () => {
    const spectator = createComponent();

    const buttons = spectator.queryAll<HTMLButtonElement>('button');
    expect(buttons.map((button) => button.getAttribute('aria-label'))).toEqual([
      'Want to play',
      'Played & liked',
      'Played & disliked',
    ]);
    expect(buttons.every((button) => button.className.includes('h-11'))).toBe(true);
  });

  it('presses only the mark the person holds', () => {
    const spectator = createComponent({ props: { myMark: 'PLAYED_DISLIKED' } });

    expect(spectator.queryAll('button').map((button) => button.getAttribute('aria-pressed'))).toEqual([
      'false',
      'false',
      'true',
    ]);
  });

  it('emits the tapped mark, and nothing while disabled', () => {
    const spectator = createComponent();
    const changed = vi.fn();
    spectator.output('markChange').subscribe(changed);

    spectator.click('[data-mark="PLAYED_LIKED"]');
    expect(changed).toHaveBeenCalledWith('PLAYED_LIKED');

    changed.mockClear();
    spectator.setInput('disabled', true);
    spectator.click('[data-mark="PLAYED_LIKED"]');
    expect(changed).not.toHaveBeenCalled();
  });

  it('shows words next to the icons unless compact', () => {
    const spectator = createComponent();
    expect(spectator.element).toHaveText('Want to play');

    spectator.setInput('compact', true);

    expect(spectator.element).not.toHaveText('Want to play');
  });
});

describe('VoteRailComponent', () => {
  const createComponent = createComponentFactory(VoteRailComponent);

  it('lists only the marks somebody gave', () => {
    const spectator = createComponent({ props: { votes: { wantToPlay: 0, playedLiked: 1, playedDisliked: 3 } } });

    expect(spectator.queryAll('lib-mark-chip').map((chip) => chip.getAttribute('data-mark'))).toEqual([
      'PLAYED_LIKED',
      'PLAYED_DISLIKED',
    ]);
  });

  it('says the whole picture in one phrase for a screen reader, in the singular where it applies', () => {
    const spectator = createComponent({ props: { votes: { wantToPlay: 1, playedLiked: 0, playedDisliked: 2 } } });

    expect(spectator.query('[data-testid="vote-rail"]')?.getAttribute('aria-label')).toBe(
      '1 person wants to play this, 2 played and disliked it',
    );
  });

  it('is not there at all when there are no votes', () => {
    const spectator = createComponent({ props: { votes: { wantToPlay: 0, playedLiked: 0, playedDisliked: 0 } } });

    expect(spectator.query('[data-testid="vote-rail"]')).toBeNull();
  });
});
