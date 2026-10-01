import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aGameDetailMock, aSwitchSearchResultMock, GameDetail, GameDetailStore, SwitchSearchResult } from '@jordylab-fe/gamecatalog/api';
import { AuthService } from '@jordylab-fe/shared/auth';
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
  const savingSwitch = signal(false);
  const removed = signal(false);
  const relinkCandidates = signal<SwitchSearchResult[]>([]);
  const changeSwitchFormat = vi.fn<GameDetailStore['changeSwitchFormat']>();
  const relinkSwitchGame = vi.fn<GameDetailStore['relinkSwitchGame']>();
  const searchRelinkCandidates = vi.fn<GameDetailStore['searchRelinkCandidates']>();
  const removeSwitchGame = vi.fn<GameDetailStore['removeSwitchGame']>();
  const isAdmin = signal(false);

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
    savingSwitch: savingSwitch.asReadonly(),
    removed: removed.asReadonly(),
    relinkCandidates: relinkCandidates.asReadonly(),
    changeSwitchFormat,
    relinkSwitchGame,
    searchRelinkCandidates,
    removeSwitchGame,
  };

  let spectator: Spectator<GameDetailComponent>;

  const createComponent = createComponentFactory({
    component: GameDetailComponent,
    providers: [
      provideRouter([]),
      { provide: GameDetailStore, useValue: storeMock },
      { provide: AuthService, useValue: { isAdmin: isAdmin.asReadonly() } },
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
    savingSwitch.set(false);
    removed.set(false);
    relinkCandidates.set([]);
    changeSwitchFormat.mockReset();
    relinkSwitchGame.mockReset();
    searchRelinkCandidates.mockReset();
    removeSwitchGame.mockReset();
    isAdmin.set(false);
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
    isAdmin.set(true);
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
    isAdmin.set(true);
    show(aGameDetailMock({ platform: 'Steam' }));
    refreshingMetadata.set(true);
    refreshingEnrichment.set(true);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Refreshing facts…');
    expect(spectator.element).toHaveText('Regenerating…');
  });

  it('hides the deterministic refresh button for non-Steam games', () => {
    isAdmin.set(true);
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

  describe('admin-only actions and Switch games', () => {
    const switchGame = aGameDetailMock({
      platform: 'Nintendo Switch',
      hosts: [{ hostname: 'Nintendo Switch', sourceType: 'SWITCH' }],
      hostFormats: { 'Nintendo Switch': 'PHYSICAL' },
    });

    it('hides refresh and Switch management from guests but still shows the format', () => {
      show(switchGame);

      expect(spectator.query('[data-testid="switch-format"]')).toHaveText('Physical');
      expect(spectator.query('[data-testid="switch-admin"]')).toBeNull();
      expect(spectator.queryAll('button').map((button) => button.textContent?.trim())).not.toContain(
        'Regenerate description'
      );
    });

    it('shows refresh and Switch management to the admin', () => {
      isAdmin.set(true);
      show(switchGame);

      expect(spectator.query('[data-testid="switch-admin"]')).toExist();
      expect(spectator.queryAll('button').map((button) => button.textContent?.trim())).toContain(
        'Regenerate description'
      );
    });

    it('changes the format', () => {
      isAdmin.set(true);
      show(switchGame);

      spectator.selectOption('#switch-detail-format', 'DIGITAL');

      expect(changeSwitchFormat).toHaveBeenCalledWith('DIGITAL');
    });

    it('searches relink candidates and relinks to the chosen one', () => {
      isAdmin.set(true);
      show(switchGame);

      spectator.typeInElement('Mario Kart', '#switch-relink-search');
      expect(searchRelinkCandidates).toHaveBeenCalledWith('Mario Kart');

      relinkCandidates.set([aSwitchSearchResultMock({ igdbGameId: 4321, title: 'Mario Kart 8 Deluxe' })]);
      spectator.detectChanges();
      spectator.click('[data-testid="relink-candidate"]');

      expect(relinkSwitchGame).toHaveBeenCalledWith(4321);
    });

    it('removes only after a second, confirming click', () => {
      isAdmin.set(true);
      show(switchGame);

      spectator.click('[data-testid="remove-switch"]');
      expect(removeSwitchGame).not.toHaveBeenCalled();

      spectator.click('[data-testid="remove-switch-confirm"]');
      expect(removeSwitchGame).toHaveBeenCalled();
    });

    it('goes back to the library once the game is removed', () => {
      const navigateByUrl = vi.spyOn(spectator.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      show(switchGame);

      removed.set(true);
      spectator.detectChanges();

      expect(navigateByUrl).toHaveBeenCalledWith('/games/grid');
    });

    it('shows no Switch section for a scanned game', () => {
      isAdmin.set(true);
      show(aGameDetailMock());

      expect(spectator.query('[data-testid="switch-format"]')).toBeNull();
      expect(spectator.query('[data-testid="switch-admin"]')).toBeNull();
    });
  });
});
