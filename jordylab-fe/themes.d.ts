export type ThemeKind = 'dark' | 'light';

export interface ThemeDefinition {
  readonly id: string;
  readonly name: string;
  readonly kind: ThemeKind;
  readonly tokens: Readonly<Record<string, string>>;
}

export const defaultDarkThemeId: string;
export const defaultLightThemeId: string;
export const themes: readonly ThemeDefinition[];
