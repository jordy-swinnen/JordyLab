export type ArtworkStatus =
  | 'PENDING'
  | 'EXTERNAL_URL'
  | 'LOCAL_FALLBACK_REQUESTED'
  | 'LOCAL_UPLOAD'
  | 'PLACEHOLDER';

export type EnrichmentStatus = 'PENDING' | 'ENRICHED' | 'FAILED';

export type SourceType = 'STEAM' | 'EMUDECK';

/** A console an admin registered: a platform (from the catalog or custom text) and the name shown for it. */
export interface GameConsole {
  id: string;
  platform: string;
  family: BrandFamily;
  chip: PlatformChip;
  name: string;
  label: string;
  gameCount: number;
}

/** A well-known console offered by the add-console autocomplete (fifth generation onward). */
export interface KnownConsole {
  name: string;
  family: BrandFamily;
  generation: number;
  handheld: boolean;
}

/** What removing a console would do, for its confirmation dialog. */
export interface ConsoleImpact {
  games: number;
  alsoElsewhere: number;
  wouldBeRemoved: number;
}

/** One IGDB match for a search on a console's platform. */
export interface ConsoleSearchResult {
  igdbGameId: number;
  title: string;
  releaseYear: number | null;
  genres: string[];
  developer: string | null;
  coverUrl: string | null;
  bannerUrl: string | null;
  alreadyOnConsole: boolean;
}

export interface ConsoleGameResponse {
  gameId: string;
  title: string;
  /** True when the game was already in the catalog (Steam, an emulator, another console) and was linked, not duplicated. */
  linkedExisting: boolean;
}

export interface ConsoleGameListItem {
  gameId: string;
  title: string;
  releaseYear: number | null;
  coverUrl: string | null;
  coverEndpoint: string | null;
}

export type ConsoleBulkStatus = 'MATCHED' | 'NO_MATCH' | 'ALREADY_PRESENT';

export interface ConsoleBulkLine {
  line: string;
  status: ConsoleBulkStatus;
  match: ConsoleSearchResult | null;
}

export interface ConsoleBulkItem {
  line: string;
  igdbGameId: number | null;
  title: string | null;
}

export interface ConsoleBulkSummary {
  added: ConsoleGameResponse[];
  skipped: string[];
  failed: { line: string; reason: string }[];
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

export type LibrarySource = 'OWNED' | 'FAMILY';

/** The four labels a game can carry about where it comes from (derived on the server, never stored). */
export type GameSource = 'STEAM_OWNED' | 'STEAM_FAMILY' | 'EMULATED' | 'CONSOLE';

export type RomStatus = 'UNKNOWN' | 'VALIDATED' | 'BROKEN';

export type BrandFamily = 'PLAYSTATION' | 'XBOX' | 'NINTENDO' | 'SEGA' | 'STEAM' | 'OTHER';

/** A platform with the chip colours the server chose for it (official brand colours, contrast-checked). */
export interface PlatformChip {
  name: string;
  family: BrandFamily;
  background: string;
  foreground: string;
  border: string | null;
}

export type PlaceKind = 'HOST_COPY' | 'STEAM_LIBRARY' | 'CONSOLE';

/** One place a game lives: a scanned host copy, Steam library membership, or a console. */
export interface Place {
  kind: PlaceKind;
  installationId: string | null;
  hostId: string | null;
  consoleId: string | null;
  /** Host display name (else hostname) or console name; never a raw hostname when a display name exists. */
  label: string;
  platform: string;
  installed: boolean | null;
  romStatus: RomStatus | null;
  librarySource: LibrarySource | null;
  familyOwners: string | null;
}

export type LibrarySyncOutcome = 'APPLIED' | 'NO_CHANGE' | 'FAILED' | 'SUSPICIOUS';

/** The three exclusive marks a person can give a game; totals are public, voters are never shown. */
export type MarkType = 'WANT_TO_PLAY' | 'PLAYED_LIKED' | 'PLAYED_DISLIKED';

export interface VoteTotals {
  wantToPlay: number;
  playedLiked: number;
  playedDisliked: number;
}

/** What setting or clearing a mark answers: the new public totals and the caller's own mark. */
export interface MarkResult {
  votes: VoteTotals;
  myMark: MarkType | null;
}

export type RomSummaryState = 'UNKNOWN' | 'VALIDATED' | 'BROKEN' | 'MIXED';

/** How the emulated copies of a game stand; absent for a game with no emulated copy. */
export interface RomSummary {
  state: RomSummaryState;
  validated: number;
  broken: number;
  unknown: number;
  total: number;
}

export type PlaceOptionKind = 'HOST' | 'CONSOLE';

/** A host or console that holds visible games, offered by the "Where" filter. */
export interface PlaceOption {
  id: string;
  kind: PlaceOptionKind;
  label: string;
}

export type MarkScope = 'ALL' | 'MINE';

export type GameSort = 'TITLE' | 'MOST_WANTED' | 'MOST_LIKED';

export interface GameSummary {
  id: string;
  title: string;
  platforms: PlatformChip[];
  sources: GameSource[];
  coverStatus: ArtworkStatus;
  coverUrl: string | null;
  coverEndpoint: string | null;
  installStatus: InstallStatus;
  localMultiplayer: boolean | null;
  votes: VoteTotals;
  myMark: MarkType | null;
  romSummary: RomSummary | null;
}

export interface GamesPage {
  content: GameSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  /** Only present when a minimum number of local players was asked for: games left out because their count is unknown. */
  unknownPlayerCount?: number | null;
}

/** A description and where it came from; the page builds its heading from these fields alone. */
export interface GameDescription {
  text: string;
  source: 'AI' | 'STEAM' | null;
  /** The model the provider reported as answering (a router's pick); null for older text or when none was reported. */
  model: string | null;
  /** The selected id, present only when it differs from {@code model} (a router, or the fallback provider). */
  requestedModel: string | null;
  writtenAt: string | null;
}

/** Where the Spec sheet's facts came from. */
export interface FactSources {
  facts: 'STEAM' | 'AI' | null;
  multiplayer: 'STEAM' | 'IGDB' | null;
}

export interface GameDetail {
  id: string;
  title: string;
  platforms: PlatformChip[];
  sources: GameSource[];
  places: Place[];
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
  description: GameDescription | null;
  factSources: FactSources;
  firstSeenAt: string;
  installStatus: InstallStatus;
  localMultiplayer: boolean | null;
  splitScreen: boolean | null;
  onlineOnly: boolean | null;
  multiplayerSource: MultiplayerSource;
  votes: VoteTotals;
  myMark: MarkType | null;
}

/** A scanning machine with the name an admin gave it (spec 013 FR-030). */
export interface Host {
  id: string;
  hostname: string;
  displayName: string | null;
  label: string;
}

/** What turning a source off would do; nothing is deleted either way. */
export interface HideImpact {
  hiddenGames: number;
  stillVisibleElsewhere: number;
}

export interface ScanSource {
  id: string;
  sourceKey: string;
  hostId: string;
  hostname: string;
  /** The name an admin gave the host, if any. */
  displayName: string | null;
  /** What to show: the display name when set, else the hostname. */
  label: string;
  sourceType: SourceType;
  platform: string;
  platformChip: PlatformChip;
  enabled: boolean;
  lastAttemptAt: string | null;
  lastSuccessAt: string | null;
  lastCheckedAt: string | null;
  lastOutcome: SyncOutcome | null;
  installedGameCount: number;
}

/** A game the person points LibBot at (the "ask about this game" entry on the game page). */
export interface AttachedGame {
  id: string;
  title: string;
  coverUrl: string | null;
  coverEndpoint: string | null;
}

export type LibBotStage = 'UNDERSTANDING' | 'SEARCHING' | 'WRITING';

export type LibBotOutcome = 'ANSWERED' | 'NO_MATCH' | 'CLARIFY' | 'OUT_OF_SCOPE' | 'EMPTY_LIBRARY';

export interface LibBotReference {
  gameId: string;
  title: string;
  platforms: PlatformChip[];
  cover: { status: ArtworkStatus; externalUrl: string | null; localUrl: string | null };
}

/** The one `answer` event of a LibBot stream. `applied` and `unknown.note` are server-written, in the question's language. */
export interface LibBotAnswer {
  outcome: LibBotOutcome;
  language: 'en' | 'nl';
  text: string;
  applied: string[];
  unknown: { count: number; note: string } | null;
  references: LibBotReference[];
}

export interface LibBotQuota {
  limit: number | null;
  remaining: number | null;
  resetsAt: string;
  exempt: boolean;
}

export type LibBotAskEvent =
  | { kind: 'stage'; stage: LibBotStage }
  | { kind: 'answer'; answer: LibBotAnswer }
  | { kind: 'error'; code: 'UNAVAILABLE' | 'INTERNAL'; retryable: boolean }
  | { kind: 'limitReached'; resetsAt: string }
  | { kind: 'rejected' };

export interface LibBotMessage {
  role: 'user' | 'assistant';
  text: string;
  /** Assistant messages only. */
  answer?: LibBotAnswer;
  /** An assistant message that stands for a failure (shown with a Try again action). */
  failed?: boolean;
}

/** How complete the visible library is: what the background fill has not managed to supply yet. */
export interface LibraryHealth {
  totalGames: number;
  gamesWithoutCover: number;
  gamesWithoutDescription: number;
  gamesPendingIndex: number;
}

export type HealthKind = 'COVER' | 'DESCRIPTION' | 'INDEX';

export interface HealthExceptions {
  kind: HealthKind;
  total: number;
  games: { id: string; title: string }[];
}

/** GET /api/gamecatalog/sources: the scan sources and the library health in one call. */
export interface SourcesOverview {
  sources: ScanSource[];
  health: LibraryHealth;
}

export type RefreshRunKind = 'DATA' | 'AI';

export type RefreshRunStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'STOPPED' | 'INTERRUPTED';

/** An admin-started bulk refresh over every game: its progress, and why it ended early if it did. */
export interface RefreshRun {
  id: string;
  kind: RefreshRunKind;
  status: RefreshRunStatus;
  total: number;
  processed: number;
  failed: number;
  stopRequested: boolean;
  failureSummary: string | null;
  startedAt: string;
  finishedAt: string | null;
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
