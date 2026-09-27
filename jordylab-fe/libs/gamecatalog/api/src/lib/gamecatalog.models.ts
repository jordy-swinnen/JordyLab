export type ArtworkStatus =
  | 'PENDING'
  | 'EXTERNAL_URL'
  | 'LOCAL_FALLBACK_REQUESTED'
  | 'LOCAL_UPLOAD'
  | 'PLACEHOLDER';

export type EnrichmentStatus = 'PENDING' | 'ENRICHED' | 'FAILED';

export type SourceType = 'STEAM' | 'EMUDECK';

export type SyncOutcome =
  | 'APPLIED'
  | 'NO_CHANGE'
  | 'SCAN_FAILED'
  | 'REJECTED';

export type ScanLibraryType = 'steam' | 'emudeck';

export type MetadataSource = 'STEAM' | 'AI';

export interface HostRef {
  hostname: string;
  sourceType: SourceType;
}

export interface GameSummary {
  id: string;
  title: string;
  platform: string;
  coverStatus: ArtworkStatus;
  coverUrl: string | null;
  coverEndpoint: string | null;
}

export interface GamesPage {
  content: GameSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface GameDetail {
  id: string;
  title: string;
  platform: string;
  hosts: HostRef[];
  coverStatus: ArtworkStatus;
  coverUrl: string | null;
  coverEndpoint: string | null;
  bannerStatus: ArtworkStatus;
  bannerUrl: string | null;
  bannerEndpoint: string | null;
  enrichmentStatus: EnrichmentStatus;
  genre: string | null;
  genres: string | null;
  developer: string | null;
  publisher: string | null;
  releaseYear: number | null;
  metadataSource: MetadataSource | null;
  maxLocalPlayers: number | null;
  onlineMultiplayer: boolean | null;
  singlePlayer: boolean | null;
  description: string | null;
  firstSeenAt: string;
}

export interface ScanSource {
  id: string;
  sourceKey: string;
  hostname: string;
  sourceType: SourceType;
  platform: string;
  enabled: boolean;
  lastAttemptAt: string | null;
  lastSuccessAt: string | null;
  lastCheckedAt: string | null;
  lastOutcome: SyncOutcome | null;
  installedGameCount: number;
}

export interface ChatGameRef {
  id: string;
  title: string;
  platform: string;
}

export interface AttachedGame {
  id: string;
  title: string;
}

export interface ChatAnswer {
  answer: string;
  games: ChatGameRef[];
  noMatch: boolean;
}

export type ChatAskResponse =
  | { kind: 'answered'; answer: ChatAnswer }
  | { kind: 'unavailable' };

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  games?: ChatGameRef[];
  unavailable?: boolean;
}
