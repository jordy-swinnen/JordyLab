/**
 * Cover plates: games without artwork get a flat colour block with big initials, so a missing
 * cover still reads as a deliberate catalogue plate. Colour is derived from the title so it is
 * stable between visits.
 */
export interface CoverPalette {
  background: string;
  color: string;
}

const PALETTE: CoverPalette[] = [
  { background: '#F2EDE4', color: '#1C0A04' }, // bone
  { background: '#FF6A3D', color: '#1C0A04' }, // flare
  { background: '#9A8BFF', color: '#1C0A04' }, // iris
  { background: '#2B2650', color: '#F2EDE4' }, // deep iris
  { background: '#6E2A17', color: '#F2EDE4' }, // rust
  { background: '#241F38', color: '#FF6A3D' }, // plum
];

const SMALL_WORDS = new Set(['the', 'of', 'a', 'an', 'and']);

export function coverPalette(title: string): CoverPalette {
  let hash = 0;
  for (const char of title) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  }

  return PALETTE[hash % PALETTE.length];
}

/** "Mega Man X" → "MMX", "The Binding of Isaac: Rebirth" → "BIR", "Celeste" → "Ce". */
export function coverInitials(title: string): string {
  const words = title
    .split(/[\s:–-]+/)
    .filter((word) => word.length > 0 && !SMALL_WORDS.has(word.toLowerCase()));

  if (words.length === 0) {
    return title.slice(0, 2);
  }
  if (words.length === 1) {
    const word = words[0];
    return word.charAt(0).toUpperCase() + word.charAt(1).toLowerCase();
  }

  return words
    .slice(0, 3)
    .map((word) => word.charAt(0).toUpperCase())
    .join('');
}
