import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aGameDetailMock, GameDetail, GameDetailStore } from '@jordylab-fe/gamecatalog/api';
import { GameDetailComponent } from './game-detail.component';

describe('GameDetailComponent', () => {
  const game = signal<GameDetail | null>(null);
  const loading = signal(true);
  const notFound = signal(false);
  const error = signal<string | null>(null);
  const refreshingMetadata = signal(false);
  const refreshingEnrichment = signal(false);
  const load = vi.fn<GameDetailStore['load']>();
  const refreshMetadata = vi.fn<GameDetailStore['refreshMetadata']>();
  const refreshEnrichment = vi.fn<GameDetailStore['refreshEnrichment']>();

  const storeMock = {
    game: game.asReadonly(),
    loading: loading.asReadonly(),
    notFound: notFound.asReadonly(),
    error: error.asReadonly(),
    refreshingMetadata: refreshingMetadata.asReadonly(),
    refreshingEnrichment: refreshingEnrichment.asReadonly(),
    load,
    refreshMetadata,
    refreshEnrichment,
  };

  let spectator: Spectator<GameDetailComponent>;

  const createComponent = createComponentFactory({
    component: GameDetailComponent,
    providers: [
      { provide: GameDetailStore, useValue: storeMock },
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: { paramMap: convertToParamMap({ id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f' }) },
        },
      },
    ],
  });

  beforeEach(() => {
    game.set(null);
    loading.set(true);
    notFound.set(false);
    error.set(null);
    refreshingMetadata.set(false);
    refreshingEnrichment.set(false);
    load.mockReset();
    refreshMetadata.mockReset();
    refreshEnrichment.mockReset();
    spectator = createComponent();
  });

  const show = (detail: GameDetail) => {
    loading.set(false);
    game.set(detail);
    spectator.detectChanges();
  };

  it('requests the game for the route id', () => {
    expect(load).toHaveBeenCalledWith('1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f');
  });

  it('renders the wide banner, cover, facts and prose for an enriched game', () => {
    show(aGameDetailMock());

    const banner = spectator.query('img[alt="Super Mario World banner"]');
    expect(banner?.getAttribute('src')).toBe('https://example.com/smw-banner.png');
    const cover = spectator.query('img[alt="Super Mario World cover"]');
    expect(cover?.getAttribute('src')).toBe('https://example.com/smw.png');
    expect(spectator.query('h2')).toHaveText('Super Mario World');
    expect(spectator.query('dl')).toHaveText('up to 2 players');
    expect(spectator.query('dl')).toHaveText('Platformer, Action');
    expect(spectator.query('p.max-w-prose')).toHaveText('A classic SNES platformer.');
    expect(spectator.element).not.toHaveText('Description unavailable.');
  });

  it('renders deterministic metadata and hosts in the spec sheet', () => {
    show(aGameDetailMock());

    const sheet = spectator.query('dl');
    expect(sheet).toHaveText('Nintendo');
    expect(sheet).toHaveText('1990');
    expect(sheet).toHaveText('jordybox');
  });

  it('renders the spark icon and an attachment link on the ask button', () => {
    show(aGameDetailMock());

    const askLink = spectator
      .queryAll('a')
      .find((anchor) => anchor.textContent?.includes('Ask the catalog'));
    expect(askLink).toBeTruthy();
    expect(askLink?.getAttribute('href')).toContain('/games/chat');
    expect(askLink?.getAttribute('href')).toContain('attach=1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f');
  });

  it('forwards the refresh actions to the store', () => {
    show(aGameDetailMock({ platform: 'Steam' }));

    const buttons = spectator.queryAll('button');
    const facts = buttons.find((button) => button.textContent?.includes('Refresh facts'));
    const regenerate = buttons.find((button) => button.textContent?.includes('Regenerate description'));
    spectator.click(facts as Element);
    spectator.click(regenerate as Element);

    expect(refreshMetadata).toHaveBeenCalled();
    expect(refreshEnrichment).toHaveBeenCalled();
  });

  it('shows a refreshing label while a refresh is in flight', () => {
    show(aGameDetailMock({ platform: 'Steam' }));
    refreshingMetadata.set(true);
    refreshingEnrichment.set(true);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Refreshing facts…');
    expect(spectator.element).toHaveText('Regenerating…');
  });

  it('hides the deterministic refresh button for non-Steam games', () => {
    show(aGameDetailMock({ platform: 'SNES' }));

    const buttons = spectator.queryAll('button');
    expect(buttons.find((button) => button.textContent?.includes('Refresh facts'))).toBeUndefined();
    expect(buttons.find((button) => button.textContent?.includes('Regenerate description'))).toBeTruthy();
  });

  it('shows a banner plate instead of an image when there is no banner', () => {
    show(aGameDetailMock({ bannerStatus: 'PLACEHOLDER', bannerUrl: null, bannerEndpoint: null }));

    expect(spectator.query('img[alt="Super Mario World banner"]')).toBeNull();
    expect(spectator.query('[aria-label="No banner for Super Mario World"]')).toBeTruthy();
  });

  it('shows the explicit unavailable state while enrichment is pending', () => {
    show(
      aGameDetailMock({
        enrichmentStatus: 'PENDING',
        metadataSource: null,
        genre: null,
        genres: null,
        developer: null,
        publisher: null,
        releaseYear: null,
        maxLocalPlayers: null,
        onlineMultiplayer: null,
        singlePlayer: null,
        description: null,
      })
    );

    expect(spectator.element).toHaveText('Description unavailable.');
    expect(spectator.element).toHaveText('has not been generated yet');
    expect(spectator.query('dl')).toBeNull();
  });

  it('shows the explicit unavailable state when enrichment failed', () => {
    show(
      aGameDetailMock({
        enrichmentStatus: 'FAILED',
        metadataSource: null,
        genre: null,
        genres: null,
        maxLocalPlayers: null,
        onlineMultiplayer: null,
        singlePlayer: null,
        description: null,
      })
    );

    expect(spectator.element).toHaveText('Description unavailable.');
    expect(spectator.element).toHaveText('could not be generated');
  });

  it('shows a not-found state for an invisible game', () => {
    loading.set(false);
    notFound.set(true);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('This game is not in your catalog.');
  });

  it('shows an error state for other failures', () => {
    loading.set(false);
    error.set('Failed to load the game.');
    spectator.detectChanges();

    expect(spectator.query('.text-destructive')).toHaveText('Failed to load the game.');
  });

  it('shows skeletons while loading', () => {
    expect(spectator.queryAll('hlm-skeleton').length).toBeGreaterThan(0);
  });
});
