import { GameSource } from '@jordylab-fe/gamecatalog/api';

/** The four labels a game can carry about where it comes from (spec 013 FR-039). */
export const SOURCE_LABELS: Record<GameSource, string> = {
  STEAM_OWNED: 'Steam (Owned)',
  STEAM_FAMILY: 'Steam (Family)',
  EMULATED: 'Emulated',
  CONSOLE: 'Console',
};

export const SOURCE_ORDER: GameSource[] = ['STEAM_OWNED', 'STEAM_FAMILY', 'EMULATED', 'CONSOLE'];
