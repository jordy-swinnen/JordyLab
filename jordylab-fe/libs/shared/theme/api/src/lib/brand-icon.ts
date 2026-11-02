/** The flask-J mark as a standalone image, in the given colours: used for the browser tab, which cannot read the page's CSS variables. */
export function brandIconDataUri(background: string, foreground: string): string {
  const markup =
    `<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48" fill="none">` +
    `<rect width="48" height="48" rx="13" fill="${background}"/>` +
    `<path d="M30 10V27C30 32.5 26 36 21 36C17.5 36 15 34.5 13.5 32" stroke="${foreground}" stroke-width="6.5" stroke-linecap="round" stroke-linejoin="round"/>` +
    `<circle cx="17" cy="21" r="3.6" fill="${foreground}"/><circle cx="21.5" cy="12.5" r="2.4" fill="${foreground}"/></svg>`;

  return `data:image/svg+xml,${encodeURIComponent(markup)}`;
}
