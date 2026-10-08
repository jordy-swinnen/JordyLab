import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable, Injector } from '@angular/core';
import { AuthService } from '@jordylab-fe/shared/auth';
import { from, map, Observable, switchMap } from 'rxjs';
import { askLibBot, LibBotAskRequest } from './libbot-stream';
import {
  GameConsole,
  ConsoleBulkItem,
  ConsoleBulkLine,
  ConsoleBulkSummary,
  ConsoleGameListItem,
  ConsoleGameResponse,
  ConsoleImpact,
  ConsoleSearchResult,
  GameDetail,
  GameSource,
  GamesPage,
  HealthExceptions,
  HealthKind,
  HideImpact,
  Host,
  InstallStatus,
  KnownConsole,
  GameSort,
  MarkResult,
  MarkScope,
  MarkType,
  Place,
  PlaceOption,
  RomStatus,
  LibBotAskEvent,
  LibBotQuota,
  PlatformChip,
  LibraryStatus,
  LibrarySyncRun,
  RefreshRun,
  RefreshRunKind,
  ScanLibraryType,
  ScanSource,
  SourcesOverview,
} from './gamecatalog.models';

export interface GamesQuery {
  search?: string;
  platform?: string[];
  where?: string[];
  installStatus?: InstallStatus;
  source?: GameSource[];
  minLocalPlayers?: number;
  romStatus?: RomStatus[];
  mark?: MarkType[];
  markScope?: MarkScope;
  sort?: GameSort;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class GameCatalogApiService {
  #http = inject(HttpClient);
  // Resolved only when LibBot is asked, so the other calls (and their tests) do not need the sign-in configuration.
  #injector = inject(Injector);

  getGames(query: GamesQuery = {}): Observable<GamesPage> {
    let params = new HttpParams();
    if (query.search) {
      params = params.set('search', query.search);
    }
    for (const platform of query.platform ?? []) {
      params = params.append('platform', platform);
    }
    for (const placeId of query.where ?? []) {
      params = params.append('where', placeId);
    }
    if (query.installStatus) {
      params = params.set('installStatus', query.installStatus);
    }
    for (const source of query.source ?? []) {
      params = params.append('source', source);
    }
    if (query.minLocalPlayers) {
      params = params.set('minLocalPlayers', query.minLocalPlayers);
    }
    for (const romStatus of query.romStatus ?? []) {
      params = params.append('romStatus', romStatus);
    }
    for (const mark of query.mark ?? []) {
      params = params.append('mark', mark);
    }
    if (query.mark?.length && query.markScope) {
      params = params.set('markScope', query.markScope);
    }
    if (query.sort && query.sort !== 'TITLE') {
      params = params.set('sort', query.sort);
    }
    if (query.page !== undefined) {
      params = params.set('page', query.page);
    }
    if (query.size !== undefined) {
      params = params.set('size', query.size);
    }

    return this.#http.get<GamesPage>('/api/gamecatalog/games', { params });
  }

  getPlatforms(): Observable<PlatformChip[]> {
    return this.#http
      .get<{ platforms: PlatformChip[] }>('/api/gamecatalog/platforms')
      .pipe(map((response) => response.platforms));
  }

  getPlaces(): Observable<PlaceOption[]> {
    return this.#http
      .get<{ places: PlaceOption[] }>('/api/gamecatalog/places')
      .pipe(map((response) => response.places));
  }

  getGame(id: string): Observable<GameDetail> {
    return this.#http.get<GameDetail>(`/api/gamecatalog/games/${id}`);
  }

  getSources(): Observable<SourcesOverview> {
    return this.#http.get<SourcesOverview>('/api/gamecatalog/sources');
  }

  getHealthExceptions(kind: HealthKind): Observable<HealthExceptions> {
    return this.#http.get<HealthExceptions>('/api/gamecatalog/sources/health/exceptions', {
      params: new HttpParams().set('kind', kind),
    });
  }

  setHostDisplayName(hostId: string, displayName: string | null): Observable<Host> {
    return this.#http.put<Host>(`/api/gamecatalog/hosts/${hostId}/display-name`, { displayName });
  }

  setMark(gameId: string, mark: MarkType | null): Observable<MarkResult> {
    return this.#http.put<MarkResult>(`/api/gamecatalog/games/${gameId}/mark`, { mark });
  }

  getHideImpact(id: string): Observable<HideImpact> {
    return this.#http.get<HideImpact>(`/api/gamecatalog/sources/${id}/hide-impact`);
  }

  setRomStatus(gameId: string, installationId: string, status: RomStatus): Observable<Place> {
    return this.#http.put<Place>(`/api/gamecatalog/games/${gameId}/installations/${installationId}/rom-status`, { status });
  }

  setSourceEnabled(id: string, enabled: boolean): Observable<{ id: string; enabled: boolean }> {
    return this.#http.put<{ id: string; enabled: boolean }>(`/api/gamecatalog/sources/${id}/enabled`, { enabled });
  }

  refreshGameMetadata(id: string): Observable<GameDetail> {
    return this.#http.post<GameDetail>(`/api/gamecatalog/games/${id}/metadata/refresh`, {});
  }

  refreshGameEnrichment(id: string): Observable<GameDetail> {
    return this.#http.post<GameDetail>(`/api/gamecatalog/games/${id}/enrichment/refresh`, {});
  }

  startRefreshRun(kind: RefreshRunKind, confirmCost: boolean): Observable<RefreshRun> {
    return this.#http.post<RefreshRun>('/api/gamecatalog/refresh-runs', { kind, confirmCost });
  }

  /** The latest run of a kind, or null when none was ever started (the server answers 204). */
  getCurrentRefreshRun(kind: RefreshRunKind): Observable<RefreshRun | null> {
    return this.#http.get<RefreshRun | null>('/api/gamecatalog/refresh-runs/current', {
      params: new HttpParams().set('kind', kind),
    });
  }

  stopRefreshRun(id: string): Observable<RefreshRun> {
    return this.#http.post<RefreshRun>(`/api/gamecatalog/refresh-runs/${id}/stop`, {});
  }

  syncOwnedLibrary(force = false): Observable<LibrarySyncRun> {
    return this.#http.post<LibrarySyncRun>('/api/gamecatalog/library/steam/sync', { force });
  }

  syncFamilyLibrary(accessToken: string, force = false): Observable<LibrarySyncRun> {
    return this.#http.post<LibrarySyncRun>('/api/gamecatalog/library/steam-family/sync', { accessToken, force });
  }

  getLibraryStatus(): Observable<LibraryStatus> {
    return this.#http.get<LibraryStatus>('/api/gamecatalog/library/status');
  }

  getScanClient(libraryType: ScanLibraryType): Observable<Blob> {
    return this.#http.get(`/api/gamecatalog/ingest/client?libraryType=${libraryType}`, {
      responseType: 'blob',
    });
  }

  /** Asks LibBot; emits stage events, then one answer or error (see {@link askLibBot}). */
  askLibBot(request: LibBotAskRequest): Observable<LibBotAskEvent> {
    return from(this.#injector.get(AuthService).getToken()).pipe(switchMap((token) => askLibBot(request, token)));
  }

  getLibBotQuota(): Observable<LibBotQuota> {
    return this.#http.get<LibBotQuota>('/api/gamecatalog/libbot/quota');
  }

  forgetLibBotConversation(conversationId: string): Observable<void> {
    return this.#http.delete<void>(`/api/gamecatalog/libbot/conversations/${conversationId}`);
  }

  getKnownConsoles(query: string): Observable<KnownConsole[]> {
    return this.#http.get<KnownConsole[]>('/api/gamecatalog/consoles/known', { params: new HttpParams().set('q', query) });
  }

  getConsoles(): Observable<GameConsole[]> {
    return this.#http.get<GameConsole[]>('/api/gamecatalog/consoles');
  }

  addConsole(platform: string, name: string | null): Observable<GameConsole> {
    return this.#http.post<GameConsole>('/api/gamecatalog/consoles', name ? { platform, name } : { platform });
  }

  renameConsole(id: string, name: string): Observable<GameConsole> {
    return this.#http.patch<GameConsole>(`/api/gamecatalog/consoles/${id}`, { name });
  }

  getConsoleImpact(id: string): Observable<ConsoleImpact> {
    return this.#http.get<ConsoleImpact>(`/api/gamecatalog/consoles/${id}/impact`);
  }

  removeConsole(id: string): Observable<void> {
    return this.#http.delete<void>(`/api/gamecatalog/consoles/${id}`);
  }

  getConsoleGames(id: string): Observable<ConsoleGameListItem[]> {
    return this.#http.get<ConsoleGameListItem[]>(`/api/gamecatalog/consoles/${id}/games`);
  }

  searchConsoleGames(id: string, query: string): Observable<ConsoleSearchResult[]> {
    return this.#http.get<ConsoleSearchResult[]>(`/api/gamecatalog/consoles/${id}/search`, {
      params: new HttpParams().set('q', query),
    });
  }

  addConsoleGame(id: string, game: { igdbGameId: number } | { title: string }): Observable<ConsoleGameResponse> {
    return this.#http.post<ConsoleGameResponse>(`/api/gamecatalog/consoles/${id}/games`, game);
  }

  relinkConsoleGame(id: string, gameId: string, igdbGameId: number): Observable<ConsoleGameResponse> {
    return this.#http.patch<ConsoleGameResponse>(`/api/gamecatalog/consoles/${id}/games/${gameId}`, { igdbGameId });
  }

  removeConsoleGame(id: string, gameId: string): Observable<void> {
    return this.#http.delete<void>(`/api/gamecatalog/consoles/${id}/games/${gameId}`);
  }

  previewConsoleBulk(id: string, lines: string[]): Observable<ConsoleBulkLine[]> {
    return this.#http
      .post<{ lines: ConsoleBulkLine[] }>(`/api/gamecatalog/consoles/${id}/games/bulk/preview`, { lines })
      .pipe(map((response) => response.lines));
  }

  confirmConsoleBulk(id: string, items: ConsoleBulkItem[]): Observable<ConsoleBulkSummary> {
    return this.#http.post<ConsoleBulkSummary>(`/api/gamecatalog/consoles/${id}/games/bulk/confirm`, { items });
  }
}

export interface CoverArtwork {
  coverUrl: string | null;
  coverEndpoint: string | null;
}

export function coverUrl(game: CoverArtwork): string | null {
  return game.coverUrl ?? game.coverEndpoint;
}

export function bannerUrl(game: GameDetail): string | null {
  return game.bannerUrl ?? game.bannerEndpoint;
}
