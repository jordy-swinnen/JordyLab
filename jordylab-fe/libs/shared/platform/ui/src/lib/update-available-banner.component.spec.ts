import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { UpdateAvailableBannerComponent } from './update-available-banner.component';

describe('UpdateAvailableBannerComponent', () => {
  const createComponent = createComponentFactory(UpdateAvailableBannerComponent);
  let spectator: Spectator<UpdateAvailableBannerComponent>;

  beforeEach(() => {
    spectator = createComponent({
      props: { versionName: '1.3.0', releaseNotes: 'Fixed things.' },
    });
  });

  it('shows the version and release notes', () => {
    expect(spectator.element).toHaveText('Update available: v1.3.0');
    expect(spectator.element).toHaveText('Fixed things.');
  });

  it('emits download when Update is clicked', () => {
    const downloadSpy = vi.fn();
    spectator.output('download').subscribe(downloadSpy);

    spectator.click('.jordylab-update-banner button:first-of-type');

    expect(downloadSpy).toHaveBeenCalled();
  });

  it('hides itself when Later is clicked', () => {
    spectator.click('.jordylab-update-banner button:last-of-type');

    expect(spectator.query('.jordylab-update-banner')).toBeFalsy();
  });
});
