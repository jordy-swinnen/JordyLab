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

  it('says it is preparing and disables Download while the link is requested', () => {
    promptKind.set('android');
    spectator.setInput('status', 'preparing');

    expect(spectator.query('[role="status"]')?.textContent).toContain('Preparing');
    expect(spectator.query<HTMLButtonElement>('.jordylab-install-dialog button')?.disabled).toBe(true);
  });

  it('tells the user where to find the file once the download has been requested', () => {
    promptKind.set('android');
    spectator.setInput('status', 'started');

    expect(spectator.query('[role="status"]')?.textContent).toContain('Download requested');
  });

  it('shows an error and offers Try again when the download could not be prepared', () => {
    promptKind.set('android');
    spectator.setInput('status', 'failed');

    expect(spectator.query('[role="alert"]')?.textContent).toContain('Could not prepare');
    expect(spectator.query('.jordylab-install-dialog button')?.textContent).toContain('Try again');
  });
});
