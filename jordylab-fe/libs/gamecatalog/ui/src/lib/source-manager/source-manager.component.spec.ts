import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { of, Subject } from 'rxjs';
import { GameCatalogApiService, ScanSource } from '@jordylab-fe/gamecatalog/api';
import { SourceManagerComponent } from './source-manager.component';

class GameCatalogApiServiceMock {
  private sourcesSubject = new Subject<ScanSource[]>();
  getSources = vi.fn(() => this.sourcesSubject.asObservable());
  setSourceEnabled = vi.fn((id: string, enabled: boolean) => of({ id, enabled }));
  getScanScript = vi.fn(() => of(new Blob(['#!/bin/sh'], { type: 'text/x-shellscript' })));

  setSources(sources: ScanSource[]) {
    this.sourcesSubject.next(sources);
  }

  failSources() {
    this.sourcesSubject.error(new Error('network error'));
  }
}

const aSource = (overrides: Partial<ScanSource> = {}): ScanSource => ({
  id: '2c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
  sourceKey: 'jordybox:STEAM',
  hostname: 'jordybox',
  sourceType: 'STEAM',
  platform: 'Steam',
  enabled: true,
  lastAttemptAt: '2026-08-02T10:20:00Z',
  lastSuccessAt: '2026-08-02T10:20:00Z',
  lastOutcome: 'APPLIED',
  installedGameCount: 412,
  ...overrides,
});

describe('SourceManagerComponent', () => {
  const createComponent = createComponentFactory({
    component: SourceManagerComponent,
    providers: [{ provide: GameCatalogApiService, useClass: GameCatalogApiServiceMock }],
  });

  const apiMock = (spectator: Spectator<SourceManagerComponent>) =>
    spectator.inject(GameCatalogApiService) as unknown as GameCatalogApiServiceMock;

  it('shows skeletons while loading', () => {
    const spectator: Spectator<SourceManagerComponent> = createComponent();

    expect(spectator.queryAll('hlm-skeleton').length).toBeGreaterThan(0);
  });

  it('renders source hostname, type, counts and last outcome', () => {
    const spectator: Spectator<SourceManagerComponent> = createComponent();
    apiMock(spectator).setSources([aSource()]);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('jordybox:STEAM');
    expect(spectator.element).toHaveText('hostname: jordybox');
    expect(spectator.element).toHaveText('Steam library');
    expect(spectator.element).toHaveText('412 installed');
    expect(spectator.element).toHaveText('APPLIED');
  });

  it('exposes Steam and EmuDeck download buttons', () => {
    const spectator: Spectator<SourceManagerComponent> = createComponent();
    apiMock(spectator).setSources([]);
    spectator.detectChanges();

    expect(spectator.query('[data-testid="download-steam-script"]')).not.toBeNull();
    expect(spectator.query('[data-testid="download-emudeck-script"]')).not.toBeNull();
  });

  it('shows an empty state when no sources exist', () => {
    const spectator: Spectator<SourceManagerComponent> = createComponent();
    apiMock(spectator).setSources([]);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('No scan sources announced yet.');
  });

  it('shows an error state when loading fails', () => {
    const spectator: Spectator<SourceManagerComponent> = createComponent();
    apiMock(spectator).failSources();
    spectator.detectChanges();

    expect(spectator.query('.text-destructive')).toHaveText('Failed to load scan sources.');
  });

  it('disables an enabled source via the toggle', () => {
    const spectator: Spectator<SourceManagerComponent> = createComponent();
    apiMock(spectator).setSources([aSource()]);
    spectator.detectChanges();

    const toggleButton = spectator.query('button[role="switch"]') as HTMLElement;
    spectator.click(toggleButton);
    spectator.detectChanges();

    expect(apiMock(spectator).setSourceEnabled).toHaveBeenCalledWith(
      '2c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
      false
    );
    expect(spectator.component.sources()[0].enabled).toBe(false);
  });

  it('enables a disabled source via the toggle', () => {
    const spectator: Spectator<SourceManagerComponent> = createComponent();
    apiMock(spectator).setSources([aSource({ enabled: false })]);
    spectator.detectChanges();

    const toggleButton = spectator.query('button[role="switch"]') as HTMLElement;
    spectator.click(toggleButton);
    spectator.detectChanges();

    expect(apiMock(spectator).setSourceEnabled).toHaveBeenCalledWith(
      '2c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
      true
    );
    expect(spectator.component.sources()[0].enabled).toBe(true);
  });
});
