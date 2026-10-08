import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aGameDetailMock, aPlatformChipMock, GameDetail, GameDetailStore, MarkStore } from '@jordylab-fe/gamecatalog/api';
import { AuthService } from '@jordylab-fe/shared/auth';
import { GameDetailComponent } from './game-detail.component';

describe('GameDetailComponent', () => {
  const game = signal<GameDetail | null>(null);
  const loading = signal(true);
  const notFound = signal(false);
  const error = signal<string | null>(null);
  const refreshingMetadata = signal(false);
  const refreshingEnrichment = signal(false);
  const savingRomStatus = signal<ReadonlySet<string>>(new Set());
  const romStatusError = signal<string | null>(null);
  const setRomStatus = vi.fn<GameDetailStore['setRomStatus']>();
  const dismissRomStatusError = vi.fn<GameDetailStore['dismissRomStatusError']>();
  const load = vi.fn<GameDetailStore['load']>();
  const refreshMetadata = vi.fn<GameDetailStore['refreshMetadata']>();
  const refreshEnrichment = vi.fn<GameDetailStore['refreshEnrichment']>();
  const isAdmin = signal(false);
  const markPendingIds = signal<ReadonlySet<string>>(new Set());
  const markError = signal<string | null>(null);
  const toggleMark = vi.fn<MarkStore['toggle']>();
  const markStoreMock = {
    stateOf: <T>(loaded: T) => loaded,
    pending: markPendingIds.asReadonly(),
    error: markError.asReadonly(),
    toggle: toggleMark,
  };

  const storeMock = {
    game: game.asReadonly(),
    loading: loading.asReadonly(),
    notFound: notFound.asReadonly(),
    error: error.asReadonly(),
    refreshingMetadata: refreshingMetadata.asReadonly(),
    refreshingEnrichment: refreshingEnrichment.asReadonly(),
    savingRomStatus: savingRomStatus.asReadonly(),
    romStatusError: romStatusError.asReadonly(),
    setRomStatus,
    dismissRomStatusError,
    load,
    refreshMetadata,
    refreshEnrichment,
  };

  let spectator: Spectator<GameDetailComponent>;

  const createComponent = createComponentFactory({
    component: GameDetailComponent,
    providers: [
      provideRouter([]),
      { provide: GameDetailStore, useValue: storeMock },
      { provide: MarkStore, useValue: markStoreMock },
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
    savingRomStatus.set(new Set());
    romStatusError.set(null);
    setRomStatus.mockReset();
    dismissRomStatusError.mockReset();
    load.mockReset();
    refreshMetadata.mockReset();
    refreshEnrichment.mockReset();
    isAdmin.set(false);
    markPendingIds.set(new Set());
    markError.set(null);
    toggleMark.mockReset();
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
    expect(spectator.query('[data-testid="about-heading"]')).toHaveText('About · written by claude-haiku-4.5');
    expect(spectator.element).not.toHaveText('Description unavailable.');
  });

  it('shows where the facts came from as the last line of the spec sheet, not in the About heading', () => {
    show(aGameDetailMock({ factSources: { facts: 'STEAM', multiplayer: 'IGDB' } }));

    expect(spectator.query('[data-testid="fact-sources"]')).toHaveText('Source · facts from Steam · multiplayer from IGDB');
    expect(spectator.query('[data-testid="about-heading"]')).not.toHaveText('facts from');
  });

  it('credits Steam when the description is Steam\'s own', () => {
    const detail = aGameDetailMock();
    show({ ...detail, description: { ...(detail.description as NonNullable<GameDetail['description']>), source: 'STEAM', model: null } });

    expect(spectator.query('[data-testid="about-heading"]')).toHaveText('About · description from Steam');
  });

  it('puts the cover over the corner of the banner on every width, with room below it', () => {
    show(aGameDetailMock());

    const cover = spectator.query('img[alt="Super Mario World cover"]')?.parentElement;
    expect(cover?.className).toContain('absolute');
    expect(cover?.className).toContain('-bottom-10');
    expect(cover?.className).not.toContain('static');
    expect(spectator.query('img[alt="Super Mario World banner"]')?.className).toContain('aspect-[16/9]');
    expect(spectator.query('img[alt="Super Mario World banner"]')?.className).toContain('sm:aspect-[16/5]');
    expect(spectator.query('.mt-16')).toBeTruthy();
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
      .find((anchor) => anchor.textContent?.includes('Ask LibBot'));
    expect(askLink).toBeTruthy();
    expect(askLink?.getAttribute('href')).toContain('/games/libbot');
    expect(askLink?.getAttribute('href')).toContain('attach=1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f');
  });

  it('forwards the refresh actions to the store', () => {
    isAdmin.set(true);
    show(aGameDetailMock({ platforms: [aPlatformChipMock({ name: 'Steam', family: 'STEAM' })] }));

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
    show(aGameDetailMock({ platforms: [aPlatformChipMock({ name: 'Steam', family: 'STEAM' })] }));
    refreshingMetadata.set(true);
    refreshingEnrichment.set(true);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Refreshing facts…');
    expect(spectator.element).toHaveText('Regenerating…');
  });

  it('hides the deterministic refresh button for non-Steam games', () => {
    isAdmin.set(true);
    show(aGameDetailMock());

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

  describe('admin-only actions', () => {
    it('hides the refresh actions from guests', () => {
      show(aGameDetailMock());

      expect(spectator.queryAll('button').map((button) => button.textContent?.trim())).not.toContain(
        'Regenerate description',
      );
    });

    it('shows the refresh actions to the admin', () => {
      isAdmin.set(true);
      show(aGameDetailMock());

      expect(spectator.queryAll('button').map((button) => button.textContent?.trim())).toContain(
        'Regenerate description',
      );
    });

    it('has no Switch or format section any more', () => {
      isAdmin.set(true);
      show(aGameDetailMock());

      expect(spectator.query('[data-testid="switch-format"]')).toBeNull();
      expect(spectator.query('[data-testid="switch-admin"]')).toBeNull();
      expect(spectator.element).not.toHaveText('Format');
    });
  });

  it('shows every platform of the game as a coloured chip', () => {
    show(
      aGameDetailMock({
        platforms: [aPlatformChipMock({ name: 'SNES' }), aPlatformChipMock({ name: 'Steam', family: 'STEAM' })],
        sources: ['EMULATED', 'STEAM_OWNED'],
      }),
    );

    const chips = spectator.queryAll('lib-platform-chip').map((chip) => chip.textContent?.trim());
    expect(chips).toEqual(['SNES', 'Steam']);
    expect(spectator.element).toHaveText('Emulated');
    expect(spectator.element).toHaveText('Steam (Owned)');
  });

  describe('ROM status per machine', () => {
    it('lists each emulated copy with its chip and a control, and nothing for a game with no emulated copy', () => {
      show(aGameDetailMock());
      expect(spectator.queryAll('[data-testid="rom-copy"]')).toHaveLength(1);
      expect(spectator.query('[data-testid="rom-copy"] lib-rom-chip')).toHaveText('Unknown');

      const detail = aGameDetailMock();
      show({ ...detail, places: [{ ...detail.places[0], romStatus: null, installationId: null }] });
      expect(spectator.query('[data-testid="rom-section"]')).toBeNull();
    });

    it('presses the current status and sends a chosen one with the copy it belongs to', () => {
      const detail = aGameDetailMock();
      show({ ...detail, places: [{ ...detail.places[0], romStatus: 'VALIDATED' }] });
      const copyId = detail.places[0].installationId as string;

      expect(spectator.query('[data-testid="rom-copy"] [data-status="VALIDATED"]')?.getAttribute('aria-pressed')).toBe('true');

      spectator.click('[data-testid="rom-copy"] [data-status="BROKEN"]');

      expect(setRomStatus).toHaveBeenCalledWith(copyId, 'BROKEN');
    });

    it('disables the controls of a copy that is being saved and shows a failure', () => {
      const detail = aGameDetailMock();
      savingRomStatus.set(new Set([detail.places[0].installationId as string]));
      romStatusError.set('Could not save the ROM status. Try again.');
      show(detail);

      const buttons = spectator.queryAll<HTMLButtonElement>('[data-testid="rom-copy"] button');
      expect(buttons.every((button) => button.disabled)).toBe(true);
      expect(spectator.query('[data-testid="rom-error"]')).toHaveText('Could not save the ROM status');
    });
  });

  describe('marks', () => {
    it('shows the three totals and the buttons, with my mark pressed and outlined', () => {
      show(aGameDetailMock({ votes: { wantToPlay: 3, playedLiked: 1, playedDisliked: 0 }, myMark: 'PLAYED_LIKED' }));

      expect(spectator.query('[data-testid="mark-totals"]')).toHaveText('3');
      expect(spectator.query('[data-testid="mark-totals"] [data-mark="PLAYED_LIKED"]')?.getAttribute('data-mine')).toBe('true');
      expect(spectator.query('[data-mark="PLAYED_LIKED"][aria-pressed]')?.getAttribute('aria-pressed')).toBe('true');
    });

    it('sends a tapped mark with the game to the store', () => {
      const detail = aGameDetailMock();
      show(detail);

      spectator.click('[data-testid="mark-buttons"] [data-mark="WANT_TO_PLAY"]');

      expect(toggleMark).toHaveBeenCalledWith(detail, 'WANT_TO_PLAY');
    });

    it('says when saving the mark failed', () => {
      markError.set('Could not save your mark. Try again.');
      show(aGameDetailMock());

      expect(spectator.query('[data-testid="mark-error"]')).toHaveText('Could not save your mark');
    });
  });

  it('names a host by the label the admin chose', () => {
    const detail = aGameDetailMock();
    show({ ...detail, places: [{ ...detail.places[0], label: 'Living room PC' }] });

    expect(spectator.element).toHaveText('Installed on · Living room PC');
  });
});
