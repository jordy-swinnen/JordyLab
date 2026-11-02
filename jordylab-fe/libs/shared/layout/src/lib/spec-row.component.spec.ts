import { createHostFactory, SpectatorHost } from '@ngneat/spectator/vitest';
import { SpecRowComponent } from './spec-row.component';

describe('SpecRowComponent', () => {
  const createHost = createHostFactory(SpecRowComponent);
  let spectator: SpectatorHost<SpecRowComponent>;

  it('shows the label and the projected value', () => {
    spectator = createHost('<lib-spec-row label="Platform">PlayStation 2</lib-spec-row>');

    expect(spectator.element).toHaveText('Platform');
    expect(spectator.element).toHaveText('PlayStation 2');
  });
});
