import { LibraryHealth, PlayerCountsHealth } from '../gamecatalog.models';

export function aPlayerCountsHealthMock(overrides: Partial<PlayerCountsHealth> = {}): PlayerCountsHealth {
  return {
    localMultiplayerGames: 105,
    filled: { IGDB: 61, THEGAMESDB: 13 },
    unknown: 31,
    flagUnknown: 40,
    unknownByReason: {
      STEAM_NO_SOURCE: 29,
      ENTRIES_DISAGREE: 2,
      NO_MATCH: 0,
      COUNT_BELOW_TWO: 0,
      CONSOLE_NOT_LISTED: 0,
      NOT_LOOKED_UP_YET: 0,
    },
    theGamesDb: { configured: true, pausedUntil: null, pauseReason: null, remainingAllowance: 938 },
    ...overrides,
  };
}

export function aLibraryHealthMock(overrides: Partial<LibraryHealth> = {}): LibraryHealth {
  return {
    totalGames: 230,
    gamesWithoutCover: 12,
    gamesWithoutDescription: 30,
    gamesPendingIndex: 5,
    playerCounts: aPlayerCountsHealthMock(),
    ...overrides,
  };
}
