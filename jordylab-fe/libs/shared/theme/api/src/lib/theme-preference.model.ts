export type ThemeId = 'content' | 'oxblood' | 'flare' | 'paper' | 'glasshouse' | 'heather';

/** What one device has chosen: follow the device's light or dark setting, or one fixed theme. */
export type ThemePreference = 'system' | ThemeId;

export type ThemeKind = 'dark' | 'light';

export interface ThemeDefinition {
  readonly id: ThemeId;
  readonly name: string;
  readonly kind: ThemeKind;
}

/** The themes in the order the picker lists them: the dark group, then the light group. Colours live in `themes.js`. */
export const THEME_DEFINITIONS: readonly ThemeDefinition[] = [
  { id: 'content', name: 'Content Led', kind: 'dark' },
  { id: 'oxblood', name: 'Oxblood', kind: 'dark' },
  { id: 'flare', name: 'Flare Orange', kind: 'dark' },
  { id: 'paper', name: 'Paper', kind: 'light' },
  { id: 'glasshouse', name: 'Glasshouse', kind: 'light' },
  { id: 'heather', name: 'Heather', kind: 'light' },
];

export const DEFAULT_DARK_THEME_ID: ThemeId = 'content';
export const DEFAULT_LIGHT_THEME_ID: ThemeId = 'paper';
export const THEME_STORAGE_KEY = 'jordylab.theme';
