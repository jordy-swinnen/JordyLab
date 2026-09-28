import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { InstallPromptStore, InstallPromptKind } from '@jordylab-fe/shared/platform/api';
import { InstallPromptComponent } from './install-prompt.component';

describe('InstallPromptComponent', () => {
  const promptKind = signal<InstallPromptKind>(null);
  const dismiss = vi.fn();

  const storeMock = {
    promptKind: promptKind.asReadonly(),
    dismiss,
  };

  const createComponent = createComponentFactory({
    component: InstallPromptComponent,
    providers: [{ provide: InstallPromptStore, useValue: storeMock }],
  });

  let spectator: Spectator<InstallPromptComponent>;

  beforeEach(() => {
    promptKind.set(null);
    dismiss.mockClear();
    spectator = createComponent();
  });

  it('renders nothing when promptKind is null', () => {
    expect(spectator.query('.jordylab-install-dialog')).toBeFalsy();
    expect(spectator.query('.jordylab-install-sheet')).toBeFalsy();
  });

  it('renders the Android dialog with 3 steps when promptKind is android', () => {
    promptKind.set('android');
    spectator.detectChanges();

    expect(spectator.query('.jordylab-install-dialog')).toBeTruthy();
    expect(spectator.queryAll('li')).toHaveLength(3);
  });

  it('emits download when the Download button is clicked', () => {
    promptKind.set('android');
    spectator.detectChanges();
    const downloadSpy = vi.fn();
    spectator.output('download').subscribe(downloadSpy);

    spectator.click('.jordylab-install-dialog button:first-of-type');

    expect(downloadSpy).toHaveBeenCalled();
  });

  it('calls store.dismiss() when Not now is clicked', () => {
    promptKind.set('android');
    spectator.detectChanges();

    spectator.click('.jordylab-install-dialog button:last-of-type');

    expect(dismiss).toHaveBeenCalled();
  });

  it('renders the iOS sheet when promptKind is ios', () => {
    promptKind.set('ios');
    spectator.detectChanges();

    expect(spectator.query('.jordylab-install-sheet')).toBeTruthy();
  });

  it('calls store.dismiss() when the iOS sheet is closed', () => {
    promptKind.set('ios');
    spectator.detectChanges();

    spectator.click('.jordylab-install-sheet button');

    expect(dismiss).toHaveBeenCalled();
  });
});
