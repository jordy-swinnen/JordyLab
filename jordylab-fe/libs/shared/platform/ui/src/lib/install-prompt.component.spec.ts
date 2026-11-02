import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { InstallPromptKind, InstallPromptStore } from '@jordylab-fe/shared/platform/api';
import { InstallPromptComponent } from './install-prompt.component';

describe('InstallPromptComponent', () => {
  const promptKind = signal<InstallPromptKind>(null);
  const canPromptInstall = signal(false);
  const dismiss = vi.fn();
  const promptInstall = vi.fn(() => Promise.resolve<'accepted' | 'dismissed' | 'unavailable'>('accepted'));

  const storeMock = {
    promptKind: promptKind.asReadonly(),
    canPromptInstall: canPromptInstall.asReadonly(),
    dismiss,
    promptInstall,
  };

  const createComponent = createComponentFactory({
    component: InstallPromptComponent,
    providers: [{ provide: InstallPromptStore, useValue: storeMock }],
  });

  let spectator: Spectator<InstallPromptComponent>;

  beforeEach(() => {
    promptKind.set(null);
    canPromptInstall.set(false);
    dismiss.mockClear();
    promptInstall.mockClear();
    spectator = createComponent();
  });

  it('renders nothing when promptKind is null', () => {
    expect(spectator.query('.jordylab-install-dialog')).toBeFalsy();
    expect(spectator.query('.jordylab-install-sheet')).toBeFalsy();
  });

  it('offers the browser install prompt when the browser provided it', () => {
    promptKind.set('android');
    canPromptInstall.set(true);
    spectator.detectChanges();

    expect(spectator.query('.jordylab-install-dialog')).toBeTruthy();
    const dialog = spectator.query('.jordylab-install-dialog');
    expect(dialog?.textContent).toContain('Install app');
    expect(dialog?.textContent).toContain('Uninstall it');
  });

  it('triggers store.promptInstall() when the Install button is clicked', () => {
    promptKind.set('android');
    canPromptInstall.set(true);
    spectator.detectChanges();

    spectator.click('.jordylab-install-dialog button:first-of-type');

    expect(promptInstall).toHaveBeenCalled();
  });

  it('falls back to browser-menu instructions when no install prompt is available', () => {
    promptKind.set('android');
    canPromptInstall.set(false);
    spectator.detectChanges();

    const dialog = spectator.query('.jordylab-install-dialog');
    expect(dialog?.textContent).toContain('browser menu');
    expect(dialog?.textContent).not.toContain('Install app\n');
    expect(spectator.queryAll('.jordylab-install-dialog button')).toHaveLength(1);
  });

  it('calls store.dismiss() when Not now is clicked', () => {
    promptKind.set('android');
    canPromptInstall.set(true);
    spectator.detectChanges();

    spectator.click('.jordylab-install-dialog button:last-of-type');

    expect(dismiss).toHaveBeenCalled();
  });

  it('renders the iOS instruction sheet with the APK-removal note', () => {
    promptKind.set('ios');
    spectator.detectChanges();

    expect(spectator.query('.jordylab-install-sheet')).toBeTruthy();
    const sheet = spectator.query('.jordylab-install-sheet');
    expect(sheet?.textContent).toContain('Add to Home Screen');
    expect(sheet?.textContent).toContain('Uninstall it');
  });

  it('calls store.dismiss() when the iOS sheet is closed', () => {
    promptKind.set('ios');
    spectator.detectChanges();

    spectator.click('.jordylab-install-sheet button');

    expect(dismiss).toHaveBeenCalled();
  });
});
