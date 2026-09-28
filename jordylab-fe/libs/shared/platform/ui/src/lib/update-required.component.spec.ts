import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { UpdateRequiredComponent } from './update-required.component';

describe('UpdateRequiredComponent', () => {
  const createComponent = createComponentFactory(UpdateRequiredComponent);
  let spectator: Spectator<UpdateRequiredComponent>;

  beforeEach(() => {
    spectator = createComponent({ props: { latestVersionName: '1.3.0' } });
  });

  it('shows the latest version name', () => {
    expect(spectator.element).toHaveText('1.3.0');
  });

  it('emits download when Update now is clicked', () => {
    const downloadSpy = vi.fn();
    spectator.output('download').subscribe(downloadSpy);

    spectator.click('button');

    expect(downloadSpy).toHaveBeenCalled();
  });
});
