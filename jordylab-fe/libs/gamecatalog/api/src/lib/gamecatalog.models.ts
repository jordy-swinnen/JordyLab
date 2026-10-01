export type ArtworkStatus =
  | 'PENDING'
  | 'EXTERNAL_URL'
  | 'LOCAL_FALLBACK_REQUESTED'
  | 'LOCAL_UPLOAD'
  | 'PLACEHOLDER';

export type EnrichmentStatus = 'PENDING' | 'ENRICHED' | 'FAILED';

export type SourceType = 'STEAM' | 'EMUDECK' | 'SWITCH';

export type SwitchGameFormat = 'PHYSICAL' | 'DIGITAL';

export interface SwitchSearchResult {
  igdbGameId: number;
  title: string;
  releaseYear: number | null;
  genres: string[];
  developer: string | null;
  coverUrl: string | null;
  bannerUrl: string | null;
}

/** Review status of one pasted line in a Switch bulk add (009 US3). */
export type SwitchBulkStatus = 'MATCH' | 'NO_MATCH' | 'ALREADY_PRESENT' | 'NEEDS_REVIEW';

/** POST /api/gamecatalog/switch/bulk/preview: one reviewed line; nothing is saved yet. */
export interface SwitchBulkLine {
  line: string;
  status: SwitchBulkStatus;
  /** IGDB matches, best first. */
  candidates: SwitchSearchResult[];
  existingGameId: string | null;
  /** Ticked by default. */
  include: boolean;
}

export interface SwitchBulkPreview {
  lines: SwitchBulkLine[];
}

/** One ticked line sent to POST /api/gamecatalog/switch/bulk/confirm; without igdbGameId it is added by title. */
export interface SwitchBulkItem {
  line: string;
  igdbGameId: number | null;
  title: string | null;
  format: SwitchGameFormat;
}

export interface SwitchBulkSummary {
  added: SwitchGameResponse[];
  alreadyPresent: string[];
  skipped: { line: string; reason: string }[];
}

/** PATCH /api/gamecatalog/switch/games/{id}: change the format and/or relink to another IGDB game (009 switch-api). */
export interface SwitchGameUpdate {
  format?: SwitchGameFormat;
  igdbGameId?: number;
}

export interface SwitchGameResponse {
  gameId: string;
  title: string;
  platform: string;
  format: SwitchGameFormat;
}

export type SyncOutcome =
  | 'APPLIED'
  | 'NO_CHANGE'
  | 'SCAN_FAILED'
  | 'REJECTED';

export type ScanLibraryType = 'steam' | 'emudeck';

export type MetadataSource = 'STEAM' | 'AI';

export type MultiplayerSource = 'STEAM' | 'IGDB' | 'UNKNOWN';

export type InstallStatus = 'INSTALLED' | 'NOT_INSTALLED' | 'ALL';

export type LibrarySource = 'OWNED' | 'FAMILY' | 'LOCAL';

export type LibrarySyncOutcome = 'APPLIED' | 'NO_CHANGE' | 'FAILED' | 'SUSPICIOUS';

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
  installStatus: InstallStatus;
  librarySource: LibrarySource;
  localMultiplayer: boolean | null;
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
  /** Format per manually tracked host (e.g. Nintendo Switch → PHYSICAL); empty for scanned hosts (009 catalog-api). */
  hostFormats: Record<string, SwitchGameFormat>;
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
  installStatus: InstallStatus;
  librarySource: LibrarySource;
  familyOwners: string[];
  localMultiplayer: boolean | null;
  splitScreen: boolean | null;
  onlineOnly: boolean | null;
  multiplayerSource: MultiplayerSource;
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
  coverUrl: string | null;
  coverEndpoint: string | null;
}

export interface AttachedGame {
  id: string;
  title: string;
  coverUrl: string | null;
  coverEndpoint: string | null;
}

export interface ChatAnswer {
  answer: string;
  games: ChatGameRef[];
  noMatch: boolean;
}

export type ChatAskResponse =
  | { kind: 'answered'; answer: ChatAnswer }
  | { kind: 'unavailable' }
  | { kind: 'limitReached'; resetsAt: string };

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  games?: ChatGameRef[];
  unavailable?: boolean;
}

export interface RefreshCount {
  processed: number;
  remaining: number;
}

export interface RefreshAll {
  metadata: RefreshCount;
  enrichment: RefreshCount;
  multiplayer: RefreshCount;
}

export interface LibrarySyncRun {
  librarySource: 'OWNED' | 'FAMILY';
  outcome: LibrarySyncOutcome;
  startedAt: string;
  finishedAt: string;
  entriesSubmitted: number;
  entriesAdded: number;
  entriesRemoved: number;
  metadataCalls: number;
  aiCalls: number;
  errorCode: string | null;
}

export interface LibrarySourceStatus {
  lastSuccessAt: string | null;
  lastOutcome: LibrarySyncOutcome | null;
  entriesActive: number;
  metadataCalls: number;
  aiCalls: number;
  familyTokenPresent: boolean;
  stale: boolean;
}

export interface LibraryStatus {
  owned: LibrarySourceStatus;
  family: LibrarySourceStatus;
  ownedConfigured: boolean;
}
