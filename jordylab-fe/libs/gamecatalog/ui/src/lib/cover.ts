/**
 * Cover plates: games without artwork get a flat colour block with big initials, so a missing
 * cover still reads as a deliberate catalogue plate. The pair of colours is one of six `plate-N` classes of the active theme,
 * picked from the title so a game keeps its plate between visits and between themes.
 */
const PLATE_COUNT = 6;

const SMALL_WORDS = new Set(['the', 'of', 'a', 'an', 'and']);

export function coverPlateClass(title: string): string {
  let hash = 0;
  for (const char of title) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  }

  return `plate-${(hash % PLATE_COUNT) + 1}`;
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
