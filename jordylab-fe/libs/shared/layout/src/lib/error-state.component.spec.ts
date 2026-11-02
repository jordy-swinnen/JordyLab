import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { ErrorStateComponent } from './error-state.component';

describe('ErrorStateComponent', () => {
  const createComponent = createComponentFactory(ErrorStateComponent);
  let spectator: Spectator<ErrorStateComponent>;

  beforeEach(() => {
    spectator = createComponent({ props: { message: "Couldn't load games. Try again in a moment." } });
  });

  it('announces the message as an alert', () => {
    expect(spectator.query('[role="alert"]')).toHaveText("Couldn't load games.");
  });

  it('emits retry when Try again is pressed', () => {
    const retry = vi.fn();
    spectator.output('retry').subscribe(retry);

    spectator.click('button');

    expect(retry).toHaveBeenCalledOnce();
  });
});
