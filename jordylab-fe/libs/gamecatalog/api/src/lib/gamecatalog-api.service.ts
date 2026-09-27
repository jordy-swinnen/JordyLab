import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, of } from 'rxjs';
import { ChatAnswer, ChatAskResponse, GameDetail, GamesPage, InstallStatus, LibrarySource, LibraryStatus, LibrarySyncRun, RefreshAll, ScanLibraryType, ScanSource } from './gamecatalog.models';

export interface GamesQuery {
  search?: string;
  platform?: string;
  host?: string;
  installStatus?: InstallStatus;
  librarySource?: LibrarySource[];
  localMultiplayer?: boolean;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class GameCatalogApiService {
  #http = inject(HttpClient);

  getGames(query: GamesQuery = {}): Observable<GamesPage> {
    let params = new HttpParams();
    if (query.search) {
      params = params.set('search', query.search);
    }
    if (query.platform) {
      params = params.set('platform', query.platform);
    }
    if (query.host) {
      params = params.set('host', query.host);
    }
    if (query.installStatus) {
      params = params.set('installStatus', query.installStatus);
    }
    if (query.librarySource && query.librarySource.length > 0) {
      params = params.set('librarySource', query.librarySource.join(','));
    }
    if (query.localMultiplayer) {
      params = params.set('localMultiplayer', 'true');
    }
    if (query.page !== undefined) {
      params = params.set('page', query.page);
    }
    if (query.size !== undefined) {
      params = params.set('size', query.size);
    }

    return this.#http.get<GamesPage>('/api/gamecatalog/games', { params });
  }

  getPlatforms(): Observable<string[]> {
    return this.#http
      .get<{ platforms: string[] }>('/api/gamecatalog/platforms')
      .pipe(map((response) => response.platforms));
  }

  getHosts(): Observable<string[]> {
    return this.#http
      .get<{ hosts: string[] }>('/api/gamecatalog/hosts')
      .pipe(map((response) => response.hosts));
  }

  getGame(id: string): Observable<GameDetail> {
    return this.#http.get<GameDetail>(`/api/gamecatalog/games/${id}`);
  }

  getSources(): Observable<ScanSource[]> {
    return this.#http
      .get<{ sources: ScanSource[] }>('/api/gamecatalog/sources')
      .pipe(map((response) => response.sources));
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

  refreshPending(): Observable<RefreshAll> {
    return this.#http.post<RefreshAll>('/api/gamecatalog/games/refresh', {});
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

  chat(question: string, gameIds: string[] = []): Observable<ChatAskResponse> {
    const body = gameIds.length > 0 ? { question, gameIds } : { question };

    return this.#http.post<ChatAnswer>('/api/gamecatalog/chat', body).pipe(
      map((answer): ChatAskResponse => ({ kind: 'answered', answer })),
      catchError((error: HttpErrorResponse) => {
        if (error.status === 503 || error.status === 400) {
          return of<ChatAskResponse>({ kind: 'unavailable' });
        }
        throw error;
      })
    );
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
